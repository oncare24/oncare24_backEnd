package com.oncare.oncare24.hospital.dto;

/**
 * LLM 분석 결과.
 *
 * @param department           추천 진료과
 * @param secondaryDepartment  차순위 진료과 (없으면 null). 첫 진료과 이외에도 가능성 있는 케이스 표현용.
 * @param confidence           분류 자신도 (0.0 ~ 1.0). 낮으면 가정의학과 권장으로 안내 가능.
 * @param reason               LLM이 그 진료과로 추론한 근거 (사용자에게 보여줄 친절한 설명)
 * @param fromLlm              true면 LLM이 분석한 결과, false면 키워드 fallback (디버깅/모니터링용)
 */
public record DepartmentResult(
        Department department,
        Department secondaryDepartment,
        double confidence,
        String reason,
        boolean fromLlm
) {
}
