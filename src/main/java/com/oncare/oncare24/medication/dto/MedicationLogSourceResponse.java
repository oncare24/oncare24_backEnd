package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationLogSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MedicationLogSourceResponse(
        @Schema(description = "복약 일정 ID", example = "15")
        Long scheduleId,
        @Schema(description = "복약명", example = "혈압약")
        String medicationName,
        @Schema(description = "복약 예정 일시", example = "2026-05-13T08:00:00")
        LocalDateTime scheduledAt,
        @Schema(description = "실제 복약 완료 일시", example = "2026-05-13T08:05:00")
        LocalDateTime takenAt,
        @Schema(description = "복약 기록 출처", example = "MANUAL")
        MedicationLogSource logSource,
        @Schema(description = "예정 시각보다 일찍 복약해도 인정되는 시간(분)", example = "30")
        Integer allowedEarlyMinutes,
        @Schema(description = "예정 시각 이후 지연 허용 시간(분)", example = "60")
        Integer allowedDelayMinutes
) {
}
