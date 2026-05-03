package com.oncare.oncare24.hospital.dto;

import java.util.List;

/**
 * 병원 추천 응답.
 *
 * @param department              추천 진료과 (한국어 - 사용자 표시용. 예: "내과")
 * @param departmentCode          진료과 코드 (시스템 식별용. 예: "01")
 * @param secondaryDepartment     차순위 진료과 한국어 (없으면 null. 예: "가정의학과")
 * @param confidence              LLM 분류 자신도 (0.0 ~ 1.0). UI에서 "분석 정확도" 표시 가능.
 * @param reason                  LLM 추론 근거 (사용자에게 보여줄 친절한 설명)
 * @param hospitals               추천 병원 리스트 (점수 내림차순)
 * @param userLatitude            실제로 사용된 사용자 위치 (어떤 폴백 단계가 적용됐는지 디버깅용)
 * @param userLongitude           실제로 사용된 사용자 위치
 * @param locationSource          위치 출처 표시 ("REQUEST", "RECENT_REPORT", "SAFETY_ZONE")
 */
public record RecommendResponse(
        String department,
        String departmentCode,
        String secondaryDepartment,
        double confidence,
        String reason,
        List<ScoredHospital> hospitals,
        double userLatitude,
        double userLongitude,
        String locationSource
) {
}
