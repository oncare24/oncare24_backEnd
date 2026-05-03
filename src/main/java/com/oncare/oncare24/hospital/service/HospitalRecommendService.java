package com.oncare.oncare24.hospital.service;

import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.DepartmentResult;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import com.oncare.oncare24.hospital.dto.RecommendRequest;
import com.oncare.oncare24.hospital.dto.RecommendResponse;
import com.oncare.oncare24.hospital.dto.ScoredHospital;
import com.oncare.oncare24.hospital.util.DepartmentNameMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/**
 * 병원 추천 전체 흐름을 조립하는 오케스트레이터.
 *
 * <pre>
 * 1. 위치 결정 (UserLocationResolver)
 * 2. LLM 분석 (LlmAnalysisService) → department + secondaryDepartment + confidence
 * 3. 일반 병원 검색 (HospitalSearchService.searchHospitals)
 * 4. 거리/영업시간/진료과일치 스코어링 + OTHER_SPECIALTY 필터링 (HospitalScoringService.scoreAndFilter)
 * 5. <b>자동 반경 확장</b>: MATCH가 0개면 15km로 재검색
 * 6. 응답 조립
 * </pre>
 *
 * 각 단계의 실패는 하위 서비스에서 처리 (LLM 실패 → fallback, NMC 실패 → 빈 리스트).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalRecommendService {

    private static final int MAX_HOSPITALS_IN_RESPONSE = 10;

    /** 자동 반경 확장 시 사용할 반경(미터). */
    private static final int EXPANDED_RADIUS_METERS = 15000;

    private final UserLocationResolver locationResolver;
    private final LlmAnalysisService llmAnalysisService;
    private final HospitalSearchService hospitalSearchService;
    private final HospitalScoringService scoringService;

    @Transactional(readOnly = true)
    public RecommendResponse recommend(Long userId, RecommendRequest request) {
        // 1. 위치 결정
        UserLocationResolver.ResolvedLocation loc = locationResolver.resolve(userId, request);

        // 2. LLM 분석
        DepartmentResult analysis = llmAnalysisService.analyze(request.symptoms());
        log.info("[Recommend] userId={}, dept={}, secondary={}, confidence={}, fromLlm={}",
                userId, analysis.department(), analysis.secondaryDepartment(),
                analysis.confidence(), analysis.fromLlm());

        // 3. 1차 병원 검색 (요청 반경)
        int requestedRadius = request.radiusOrDefault();
        List<ScoredHospital> scored = searchAndScore(
                loc, requestedRadius, analysis.department());

        // 4. 자동 반경 확장: MATCH 0개 + 확장 가능 조건 → 15km 재검색
        if (shouldExpandRadius(scored, requestedRadius, analysis.department())) {
            log.info("[Recommend] auto-expanding radius {}m → {}m (no exact match found)",
                    requestedRadius, EXPANDED_RADIUS_METERS);
            scored = searchAndScore(loc, EXPANDED_RADIUS_METERS, analysis.department());
        }

        // 5. 상위 N개
        List<ScoredHospital> top = scored.stream().limit(MAX_HOSPITALS_IN_RESPONSE).toList();

        // 6. 응답 조립
        return new RecommendResponse(
                analysis.department().getKorean(),
                analysis.department().getCode(),
                analysis.secondaryDepartment() != null ? analysis.secondaryDepartment().getKorean() : null,
                analysis.confidence(),
                analysis.reason(),
                top,
                loc.latitude(),
                loc.longitude(),
                loc.source()
        );
    }

    /**
     * 검색 + 스코어링 + 필터링을 한 번에 수행. 자동 확장 전후로 동일 로직 사용.
     */
    private List<ScoredHospital> searchAndScore(
            UserLocationResolver.ResolvedLocation loc, int radius, Department department) {

        List<HospitalInfo> hospitals = hospitalSearchService.searchHospitals(
                loc.latitude(), loc.longitude(), radius, department);

        return scoringService.scoreAndFilter(
                hospitals, loc.latitude(), loc.longitude(), LocalTime.now(), department);
    }

    /**
     * 자동 반경 확장 조건:
     * <ul>
     *     <li>진료과가 이름 매칭 가능 (정형외과/치과/안과 등)</li>
     *     <li>현재 반경이 확장 반경보다 작음</li>
     *     <li>현재 결과에 MATCH가 0개</li>
     * </ul>
     */
    private boolean shouldExpandRadius(
            List<ScoredHospital> currentResult, int currentRadius, Department department) {
        if (department == null || !DepartmentNameMatcher.isFilterable(department)) {
            return false;
        }
        if (currentRadius >= EXPANDED_RADIUS_METERS) {
            return false;
        }
        return HospitalScoringService.countExactMatch(currentResult, department) == 0;
    }
}
