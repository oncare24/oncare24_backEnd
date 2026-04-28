package com.oncare.oncare24.hospital.dto;

import java.util.List;

/**
 * 병원 추천 응답.
 *
 * @param department          추천 진료과 (한국어 - 사용자 표시용. 예: "내과")
 * @param departmentCode      진료과 코드 (시스템 식별용. 예: "01")
 * @param urgency             응급도. HIGH면 hospitals는 응급의료기관 리스트.
 * @param reason              LLM 추론 근거 (사용자에게 보여줄 친절한 설명)
 * @param emergencyAlert      응급 케이스에 표시할 경고 메시지. urgency=HIGH일 때만 채워짐.
 * @param hospitals           추천 병원 리스트 (점수 내림차순)
 * @param userLatitude        실제로 사용된 사용자 위치 (어떤 폴백 단계가 적용됐는지 디버깅용)
 * @param userLongitude       실제로 사용된 사용자 위치
 * @param locationSource      위치 출처 표시 ("REQUEST", "RECENT_REPORT", "SAFETY_ZONE")
 */
public record RecommendResponse(
        String department,
        String departmentCode,
        Urgency urgency,
        String reason,
        String emergencyAlert,
        List<ScoredHospital> hospitals,
        double userLatitude,
        double userLongitude,
        String locationSource
) {
}
