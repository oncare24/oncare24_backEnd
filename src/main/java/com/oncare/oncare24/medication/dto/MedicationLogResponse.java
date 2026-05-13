package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationLog;
import com.oncare.oncare24.medication.entity.MedicationLogSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MedicationLogResponse(
        @Schema(description = "복약 기록 ID", example = "30")
        Long logId,
        @Schema(description = "피보호자 ID", example = "2")
        Long wardId,
        @Schema(description = "복약 일정 ID", example = "15")
        Long scheduleId,
        @Schema(description = "복약명", example = "혈압약")
        String medicationName,
        @Schema(description = "실제 복약 완료 일시", example = "2026-05-13T08:05:00")
        LocalDateTime takenAt,
        @Schema(description = "복약 기록 출처", example = "MANUAL")
        MedicationLogSource logSource,
        @Schema(description = "복약 기록 생성 일시", example = "2026-05-13T08:05:10")
        LocalDateTime createdAt
) {
    public static MedicationLogResponse from(MedicationLog log) {
        return new MedicationLogResponse(
                log.getId(),
                log.getWardId(),
                log.getScheduleId(),
                log.getMedicationName(),
                log.getTakenAt(),
                log.getLogSource(),
                log.getCreatedAt()
        );
    }

    public static MedicationLogResponse from(MedicationLog log, MedicationLogPayload payload) {
        return new MedicationLogResponse(
                log.getId(),
                log.getWardId(),
                log.getScheduleId(),
                payload.medicationName(),
                payload.takenAt(),
                payload.logSource(),
                log.getCreatedAt()
        );
    }
}
