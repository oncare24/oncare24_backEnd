package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationLogSource;

import java.time.LocalDateTime;

public record MedicationLogSourceResponse(
        Long scheduleId,
        String medicationName,
        LocalDateTime scheduledAt,
        LocalDateTime takenAt,
        MedicationLogSource logSource,
        Integer allowedEarlyMinutes,
        Integer allowedDelayMinutes
) {
}
