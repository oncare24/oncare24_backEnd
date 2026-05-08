package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationLog;
import com.oncare.oncare24.medication.entity.MedicationLogSource;

import java.time.LocalDateTime;

public record MedicationLogResponse(
        Long logId,
        Long wardId,
        Long scheduleId,
        String medicationName,
        LocalDateTime takenAt,
        MedicationLogSource logSource,
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
}
