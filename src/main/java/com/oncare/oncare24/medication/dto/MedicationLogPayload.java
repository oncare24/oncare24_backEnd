package com.oncare.oncare24.medication.dto;

import java.time.LocalDateTime;

import com.oncare.oncare24.medication.entity.MedicationLogSource;

public record MedicationLogPayload(
        Long scheduleId,
        LocalDateTime plannedAt,
        LocalDateTime takenAt,
        String medicationName,
        MedicationLogSource logSource,
        Integer allowedEarlyMinutes,
        Integer allowedDelayMinutes
) {
}
