package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationLogSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateMedicationLogRequest(
        @NotNull
        @Schema(description = "복약 기록을 저장할 피보호자 ID", example = "2")
        Long wardId,

        @Schema(description = "연결된 복약 일정 ID. 일정 없이 직접 기록하는 경우 비울 수 있습니다.", example = "15")
        Long scheduleId,

        @Schema(description = "실제 복약 완료 일시", example = "2026-05-13T08:05:00")
        LocalDateTime takenAt,

        @Schema(description = "복약명. 일정 없이 직접 기록하는 경우 사용합니다.", example = "혈압약")
        @Size(max = 100)
        String medicationName,

        @Schema(description = "복약 기록 출처", example = "MANUAL")
        MedicationLogSource logSource
) {
}
