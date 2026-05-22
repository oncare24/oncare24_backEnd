package com.oncare.oncare24.elder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 어르신(ELDER) 시점 — 나와 ACCEPTED 매핑된 보호자 정보.
 * 위치 추적 시작 여부 판단(보호자 0명 가드) + 향후 보호자 연동 UI에 사용.
 */
public record MyGuardianResponse(
        @Schema(description = "보호자 사용자 ID", example = "2")
        Long guardianId,

        @Schema(description = "보호자 이름", example = "홍길동")
        String name,

        @Schema(description = "매핑 수락 시각 (= guardian_ward.updatedAt)")
        LocalDateTime acceptedAt
) {
}