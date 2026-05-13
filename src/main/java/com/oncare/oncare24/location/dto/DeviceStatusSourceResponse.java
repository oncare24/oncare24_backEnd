package com.oncare.oncare24.location.dto;

import com.oncare.oncare24.location.entity.DeviceState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record DeviceStatusSourceResponse(
        @Schema(description = "디바이스 상태", example = "ONLINE")
        DeviceState deviceStatus,
        @Schema(description = "마지막 활동 일시", example = "2026-05-13T10:00:00")
        LocalDateTime lastActiveAt,
        @Schema(description = "연결 끊김 감지 일시", example = "2026-05-13T10:30:00")
        LocalDateTime disconnectedAt,
        @Schema(description = "상태 보고 일시", example = "2026-05-13T10:00:00")
        LocalDateTime reportedAt
) {
}
