package com.oncare.oncare24.hospital.service;

import com.oncare.oncare24.hospital.dto.DepartmentResult;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import com.oncare.oncare24.hospital.dto.RecommendRequest;
import com.oncare.oncare24.hospital.dto.RecommendResponse;
import com.oncare.oncare24.hospital.dto.ScoredHospital;
import com.oncare.oncare24.hospital.dto.Urgency;
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
 * 2. LLM 분석 (LlmAnalysisService)
 * 3. 응급도 분기:
 *    - HIGH:  HospitalSearchService.searchEmergencyRooms()
 *    - 외:    HospitalSearchService.searchHospitals(department)
 * 4. 거리/영업시간 스코어링 + 정렬 (HospitalScoringService)
 * 5. 응답 조립
 * </pre>
 *
 * 각 단계의 실패는 하위 서비스에서 처리 (LLM 실패 → fallback, NMC 실패 → 빈 리스트).
 * 이 오케스트레이터는 흐름과 응답 조립에만 집중.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalRecommendService {

    private static final int MAX_HOSPITALS_IN_RESPONSE = 10;

    private static final String EMERGENCY_ALERT =
            "심각한 응급 증상으로 보입니다. 가까운 응급실을 표시했지만, 즉시 119에 신고하시거나 보호자에게 알리세요.";

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
        log.info("[Recommend] userId={}, dept={}, urgency={}, fromLlm={}",
                userId, analysis.department(), analysis.urgency(), analysis.fromLlm());

        // 3. 응급도에 따라 검색 종류 분기
        List<HospitalInfo> hospitals = analysis.urgency() == Urgency.HIGH
                ? hospitalSearchService.searchEmergencyRooms(loc.latitude(), loc.longitude(), request.radiusOrDefault())
                : hospitalSearchService.searchHospitals(loc.latitude(), loc.longitude(), request.radiusOrDefault(), analysis.department());

        // 4. 스코어링 + 정렬 + 상위 N개
        List<ScoredHospital> scored = scoringService.score(hospitals, loc.latitude(), loc.longitude(), LocalTime.now());
        List<ScoredHospital> top = scored.stream().limit(MAX_HOSPITALS_IN_RESPONSE).toList();

        // 5. 응답 조립
        return new RecommendResponse(
                analysis.department().getKorean(),
                analysis.department().getCode(),
                analysis.urgency(),
                analysis.reason(),
                analysis.urgency() == Urgency.HIGH ? EMERGENCY_ALERT : null,
                top,
                loc.latitude(),
                loc.longitude(),
                loc.source()
        );
    }
}
