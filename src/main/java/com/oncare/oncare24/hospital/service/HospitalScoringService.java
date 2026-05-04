package com.oncare.oncare24.hospital.service;

import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import com.oncare.oncare24.hospital.dto.ScoredHospital;
import com.oncare.oncare24.hospital.util.DepartmentNameMatcher;
import com.oncare.oncare24.hospital.util.DepartmentNameMatcher.MatchResult;
import com.oncare.oncare24.location.util.Haversine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * 병원 리스트에 거리/영업중/진료과 일치도 가중치를 적용하여 점수를 매기고 정렬한다.
 * <p>
 * <b>점수 공식</b> (높을수록 추천):
 * <ul>
 *     <li>기본 점수 100점에서 거리당 차감 (1km당 -10점)</li>
 *     <li>현재 영업 중이면 +20점</li>
 *     <li><b>진료과 이름 매칭</b>: 일치 +100, 다른 진료과 표시 -50, 종합병원/의원 변동 없음</li>
 * </ul>
 *
 * <p><b>UX 향상 — {@link #scoreAndFilter}</b>: 점수만으로는 OTHER_SPECIALTY 병원이 페널티 받아도
 * 결과 리스트에 끼어 보임 (예: "정형외과" 검색 결과에 "안과의원"이 하단에 노출). 이를 방지하기 위해
 * MATCH 또는 NEUTRAL이 1개 이상 있으면 OTHER_SPECIALTY를 통째로 숨긴다.
 *
 * <p><b>왜 외부에서 거리 계산을 따로?</b>
 * 클라이언트(앱)에 거리값을 함께 보여주려면 응답 DTO에 distanceMeters 필드가 필요해서
 * 점수와 함께 미리 계산해 둔다.
 */
@Slf4j
@Service
public class HospitalScoringService {

    /** 거리 차감 계수: 1km당 -10점. */
    private static final double DISTANCE_PENALTY_PER_KM = 10.0;

    /** 영업 중 가산점. */
    private static final double OPEN_NOW_BONUS = 20.0;

    /** 진료과 이름 일치 가산점. 거리 10km 차이까지 압도하도록 +100. */
    private static final double DEPARTMENT_MATCH_BONUS = 100.0;

    /** 다른 진료과 표시 시 페널티. 정렬상 하위로 밀려남. */
    private static final double OTHER_SPECIALTY_PENALTY = 50.0;

    /**
     * 진료과를 고려하지 않는 스코어링.
     */
    public List<ScoredHospital> score(
            List<HospitalInfo> hospitals, double userLat, double userLon, LocalTime now) {
        return score(hospitals, userLat, userLon, now, null);
    }

    /**
     * 진료과를 고려한 스코어링.
     */
    public List<ScoredHospital> score(
            List<HospitalInfo> hospitals,
            double userLat,
            double userLon,
            LocalTime now,
            Department department) {

        boolean applyDeptMatch = department != null && DepartmentNameMatcher.isFilterable(department);

        if (department != null && !applyDeptMatch) {
            log.debug("[Scoring] dept={} is not name-filterable, skip dept boost", department);
        }

        List<ScoredHospital> scored = hospitals.stream()
                .map(h -> scoreOne(h, userLat, userLon, now, applyDeptMatch ? department : null))
                .sorted(Comparator.comparingDouble(ScoredHospital::score).reversed())
                .toList();

        if (applyDeptMatch) {
            long matchCount = hospitals.stream()
                    .filter(h -> DepartmentNameMatcher.nameMatches(h.name(), department))
                    .count();
            log.info("[Scoring] dept={} matched={}/{}", department, matchCount, hospitals.size());
        }

        return scored;
    }

    /**
     * 스코어링 + OTHER_SPECIALTY 자동 필터링.
     * <p>
     * 동작:
     * <ol>
     *     <li>{@link #score(List, double, double, LocalTime, Department)} 호출하여 전체 점수 매김</li>
     *     <li>진료과 매칭 가능한 경우, 결과를 다음 규칙으로 필터:
     *         <ul>
     *             <li>MATCH 또는 NEUTRAL이 1개 이상 → OTHER_SPECIALTY 전부 제외</li>
     *             <li>전부 OTHER_SPECIALTY → 차선책으로 그대로 노출 (빈 화면 방지)</li>
     *         </ul></li>
     * </ol>
     *
     * <p><b>예시</b> (정형외과 검색, 22개 병원 반환):
     * <ul>
     *     <li>matched=0, neutral=2, other=20 → neutral 2개만 표시 (종합병원/일반의원)</li>
     *     <li>matched=3, neutral=2, other=17 → matched + neutral = 5개 표시</li>
     *     <li>matched=0, neutral=0, other=22 → 차선책으로 22개 그대로 (이런 경우는 매우 드뭄)</li>
     * </ul>
     *
     * @return 필터링 후 점수 내림차순 정렬된 병원 리스트
     */
    public List<ScoredHospital> scoreAndFilter(
            List<HospitalInfo> hospitals,
            double userLat,
            double userLon,
            LocalTime now,
            Department department) {

        List<ScoredHospital> scored = score(hospitals, userLat, userLon, now, department);

        // 진료과 매칭 불가능한 경우 (가정의학과/응급의학과/기타) → 필터 적용 안 함
        if (department == null || !DepartmentNameMatcher.isFilterable(department)) {
            return scored;
        }

        List<ScoredHospital> nonOther = scored.stream()
                .filter(h -> DepartmentNameMatcher.classify(h.name(), department)
                        != MatchResult.OTHER_SPECIALTY)
                .toList();

        if (!nonOther.isEmpty()) {
            log.info("[Scoring] filtered: total={}, kept(MATCH+NEUTRAL)={}, dropped(OTHER_SPECIALTY)={}",
                    scored.size(), nonOther.size(), scored.size() - nonOther.size());
            return nonOther;
        }

        // 모두 OTHER_SPECIALTY인 매우 드문 케이스 — 차선책으로 그대로 노출
        log.warn("[Scoring] all hospitals are OTHER_SPECIALTY for dept={}, falling back to full list",
                department);
        return scored;
    }

    /**
     * 정렬된 병원 리스트에서 검색 진료과와 정확히 매칭되는 병원 개수를 센다.
     * 자동 반경 확장 트리거 판단에 사용.
     *
     * @return MATCH 개수 (진료과 null이면 0)
     */
    public static int countExactMatch(List<ScoredHospital> scored, Department department) {
        if (scored == null || department == null) return 0;
        return (int) scored.stream()
                .filter(h -> DepartmentNameMatcher.nameMatches(h.name(), department))
                .count();
    }

    private ScoredHospital scoreOne(
            HospitalInfo h, double userLat, double userLon, LocalTime now, Department department) {

        int distanceMeters = (int) Haversine.distance(userLat, userLon, h.latitude(), h.longitude());
        Boolean openNow = isOpenAt(h, now);

        double score = 100.0;
        score -= (distanceMeters / 1000.0) * DISTANCE_PENALTY_PER_KM;
        if (Boolean.TRUE.equals(openNow)) {
            score += OPEN_NOW_BONUS;
        }
        if (department != null) {
            score += departmentMatchScore(h.name(), department);
        }

        return new ScoredHospital(
                h.name(),
                h.address(),
                h.tel(),
                h.latitude(),
                h.longitude(),
                distanceMeters,
                openNow,
                score
        );
    }

    /**
     * 병원 이름의 진료과 일치도에 따른 가/감점.
     */
    private double departmentMatchScore(String hospitalName, Department department) {
        MatchResult result = DepartmentNameMatcher.classify(hospitalName, department);
        return switch (result) {
            case MATCH -> DEPARTMENT_MATCH_BONUS;
            case OTHER_SPECIALTY -> -OTHER_SPECIALTY_PENALTY;
            case NEUTRAL -> 0.0;
        };
    }

    /**
     * NMC 시간 형식: "HHmm" 4자리 문자열 (예: 0900, 1830).
     * 정보가 없거나 파싱 실패 시 null 반환 (UI에서 "영업 정보 없음"으로 표시).
     */
    private Boolean isOpenAt(HospitalInfo h, LocalTime now) {
        if (h.weekdayOpenTime() == null || h.weekdayCloseTime() == null) return null;
        if (h.weekdayOpenTime().isBlank() || h.weekdayCloseTime().isBlank()) return null;
        try {
            LocalTime open = parseHHmm(h.weekdayOpenTime());
            LocalTime close = parseHHmm(h.weekdayCloseTime());
            if (open == null || close == null) return null;
            return !now.isBefore(open) && now.isBefore(close);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime parseHHmm(String hhmm) {
        if (hhmm.length() != 4) return null;
        int h = Integer.parseInt(hhmm.substring(0, 2));
        int m = Integer.parseInt(hhmm.substring(2, 4));
        if (h < 0 || h > 23 || m < 0 || m > 59) return null;
        return LocalTime.of(h, m);
    }
}
