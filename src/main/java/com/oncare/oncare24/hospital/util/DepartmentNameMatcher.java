package com.oncare.oncare24.hospital.util;

import com.oncare.oncare24.hospital.dto.Department;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 병원 이름(예: "가우정치과", "구미봄안과의원")을 기준으로 진료과 매칭을 판정하는 유틸.
 *
 * <p><b>왜 이름 매칭인가?</b><br>
 * NMC API의 {@code QD} (dgsbjtCd) 파라미터는 코드 매핑 검증이 어려워 비활성화 상태.
 * 대신 한국 병의원 명명 관행 — 거의 모든 클리닉이 이름에 진료과를 포함 — 을 활용한다.
 * 예: "가우정치과", "구미봄안과의원", "김정형외과", "이비인후과의원".
 *
 * <p><b>3-way 분류</b>:
 * <ul>
 *   <li>{@link MatchResult#MATCH} - 이름이 검색 진료과와 일치 (점수 부스트 대상)</li>
 *   <li>{@link MatchResult#OTHER_SPECIALTY} - 이름에 다른 진료과가 명시 (페널티 대상)</li>
 *   <li>{@link MatchResult#NEUTRAL} - 종합병원/의원/보건소 등 진료과 미명시 (점수 변동 없음)</li>
 * </ul>
 *
 * <p><b>주의: "외과"는 정형외과/신경외과/성형외과/흉부외과의 부분 문자열</b>이므로
 * SURGERY 매칭 시 별도로 안티-매치 처리한다.
 */
public final class DepartmentNameMatcher {

    private DepartmentNameMatcher() {}

    public enum MatchResult {
        /** 이름이 검색 진료과와 일치 */
        MATCH,
        /** 이름이 다른 (검색 외) 진료과를 명시 */
        OTHER_SPECIALTY,
        /** 진료과 표시 없음 (종합병원, 의원, 의료원 등) */
        NEUTRAL
    }

    /**
     * 진료과별 매칭 키워드. 빈 리스트는 "이름 매칭으로 식별 불가능한 진료과"를 의미하며
     * 해당 진료과 검색 시 필터링/스코어링을 적용하지 않는다.
     *
     * <p><b>매칭 불가능 진료과:</b>
     * <ul>
     *   <li>SURGERY(외과): "외과"는 정형/신경/성형/흉부의 부분 문자열이라 별도 처리 ({@link #matchesSurgery})</li>
     *   <li>FAMILY_MEDICINE(가정의학과): 흔히 "OO의원"으로 표기되어 식별 불가</li>
     *   <li>EMERGENCY_MEDICINE(응급의학과): 별도 응급의료기관 검색 경로로 처리됨</li>
     *   <li>OTHER, 그 외 검사·영상 계열: 일반 환자 자가 진료 대상 아님</li>
     * </ul>
     */
    private static final Map<Department, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry(Department.DENTISTRY, List.of("치과")),
            Map.entry(Department.OPHTHALMOLOGY, List.of("안과")),
            Map.entry(Department.OTOLARYNGOLOGY, List.of("이비인후과")),
            Map.entry(Department.DERMATOLOGY, List.of("피부과")),
            Map.entry(Department.ORTHOPEDICS, List.of("정형외과")),
            Map.entry(Department.NEUROSURGERY, List.of("신경외과")),
            Map.entry(Department.PLASTIC_SURGERY, List.of("성형외과")),
            Map.entry(Department.OBSTETRICS, List.of("산부인과", "여성병원", "여성의원")),
            Map.entry(Department.PEDIATRICS, List.of("소아청소년과", "소아과", "어린이병원")),
            Map.entry(Department.PSYCHIATRY, List.of("정신건강의학", "정신의학", "정신과")),
            Map.entry(Department.UROLOGY, List.of("비뇨의학", "비뇨기")),
            Map.entry(Department.NEUROLOGY, List.of("신경과")),
            Map.entry(Department.CARDIOLOGY, List.of("심장", "순환기")),
            Map.entry(Department.REHABILITATION, List.of("재활의학", "재활병원")),
            Map.entry(Department.ORIENTAL_MEDICINE, List.of("한의원", "한방", "한의병원")),
            Map.entry(Department.INTERNAL_MEDICINE, List.of("내과")),
            // 매칭 불가능: 빈 리스트 (스코어링 영향 없음)
            Map.entry(Department.SURGERY, List.of()),
            Map.entry(Department.FAMILY_MEDICINE, List.of()),
            Map.entry(Department.EMERGENCY_MEDICINE, List.of()),
            Map.entry(Department.ANESTHESIOLOGY, List.of()),
            Map.entry(Department.RADIOLOGY, List.of()),
            Map.entry(Department.PATHOLOGY, List.of()),
            Map.entry(Department.LAB_MEDICINE, List.of()),
            Map.entry(Department.NUCLEAR_MEDICINE, List.of()),
            Map.entry(Department.OTHER, List.of())
    );

    /**
     * Department enum에 별도 항목이 없는 진료과 이름 패턴.
     * <p>OTHER_SPECIALTY 판정 시 추가로 검사한다. 예: "흉부외과", "산업의학과".
     */
    private static final List<String> EXTRA_SPECIALTY_PATTERNS = List.of(
            "흉부외과",
            "산업의학과"
    );

    /**
     * "외과" 단독 매칭. 정형/신경/성형/흉부 외과의 부분 문자열이므로 안티-매치 필요.
     */
    private static boolean matchesSurgery(String name) {
        return name.contains("외과")
                && !name.contains("정형외과")
                && !name.contains("신경외과")
                && !name.contains("성형외과")
                && !name.contains("흉부외과");
    }

    /**
     * 병원 이름이 특정 진료과와 매칭되는지 검사 (단일 진료과 검사용).
     *
     * @param hospitalName 병원명 (null/blank 안전)
     * @param dept         검사 대상 진료과
     * @return 매칭 여부
     */
    public static boolean nameMatches(String hospitalName, Department dept) {
        if (hospitalName == null || hospitalName.isBlank() || dept == null) {
            return false;
        }
        if (dept == Department.SURGERY) {
            return matchesSurgery(hospitalName);
        }
        List<String> keywords = KEYWORDS.getOrDefault(dept, List.of());
        return keywords.stream().anyMatch(hospitalName::contains);
    }

    /**
     * 병원 이름을 검색 진료과 기준으로 3-way 분류.
     *
     * <p>알고리즘:
     * <ol>
     *   <li>먼저 검색 진료과({@code targetDept})와 매칭되면 {@link MatchResult#MATCH}</li>
     *   <li>그렇지 않고 다른 진료과 키워드를 포함하면 {@link MatchResult#OTHER_SPECIALTY}</li>
     *   <li>그 외(종합병원/의원/보건소 등 진료과 미명시)는 {@link MatchResult#NEUTRAL}</li>
     * </ol>
     *
     * <p><b>예시</b> (targetDept=DENTISTRY):
     * <ul>
     *   <li>"가우정치과" → MATCH</li>
     *   <li>"구미봄안과의원" → OTHER_SPECIALTY</li>
     *   <li>"구미차병원" → NEUTRAL</li>
     *   <li>"OO보건소" → NEUTRAL</li>
     * </ul>
     */
    public static MatchResult classify(String hospitalName, Department targetDept) {
        if (hospitalName == null || hospitalName.isBlank() || targetDept == null) {
            return MatchResult.NEUTRAL;
        }

        // 1) 검색 진료과와 매칭?
        if (nameMatches(hospitalName, targetDept)) {
            return MatchResult.MATCH;
        }

        // 2) 다른 진료과 키워드를 포함?
        // 가정의학과/응급의학과/기타는 일반 명칭("의원" 등)이라 OTHER_SPECIALTY 판정에서 제외
        boolean hasOther = Arrays.stream(Department.values())
                .filter(d -> d != targetDept)
                .filter(d -> !KEYWORDS.getOrDefault(d, List.of()).isEmpty()
                        || d == Department.SURGERY)  // SURGERY는 키워드 없지만 별도 매칭 로직 존재
                .anyMatch(d -> nameMatches(hospitalName, d));

        if (hasOther) {
            return MatchResult.OTHER_SPECIALTY;
        }

        // 3) Department enum에 없는 추가 진료과 패턴 (예: 흉부외과)
        if (EXTRA_SPECIALTY_PATTERNS.stream().anyMatch(hospitalName::contains)) {
            return MatchResult.OTHER_SPECIALTY;
        }

        return MatchResult.NEUTRAL;
    }

    /**
     * 진료과가 이름 매칭 가능한지 (스코어링 적용 대상인지) 판정.
     * SURGERY/FAMILY_MEDICINE/EMERGENCY_MEDICINE/OTHER 등은 false.
     */
    public static boolean isFilterable(Department dept) {
        if (dept == null) return false;
        if (dept == Department.SURGERY) return true;  // 별도 매칭 로직 있음
        return !KEYWORDS.getOrDefault(dept, List.of()).isEmpty();
    }
}
