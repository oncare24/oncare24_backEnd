package com.oncare.oncare24.hospital.dto;

/**
 * LLM 분석 결과.
 *
 * @param department  추천 진료과
 * @param urgency     응급도
 * @param reason      LLM이 그 진료과/응급도를 추론한 근거 (사용자에게 보여줄 친절한 설명)
 * @param fromLlm     true면 LLM이 분석한 결과, false면 키워드 fallback (디버깅/모니터링용)
 */
public record DepartmentResult(
        Department department,
        Urgency urgency,
        String reason,
        boolean fromLlm
) {
}
