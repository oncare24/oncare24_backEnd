package com.oncare.oncare24.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** 4-3 봉지 시각 이동 요청. */
public record MovePacketTimeRequest(
        @Schema(description = "이동 전 시각", example = "08:00:00")
        @NotNull LocalTime fromTime,
        @Schema(description = "이동 후 시각", example = "09:00:00")
        @NotNull LocalTime toTime
) {
}
