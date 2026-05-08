package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationAnalysisStatus;

import java.time.LocalDateTime;

public record MedicationAnalysisResult(
        Long wardId,
        Long scheduleId,
        String medicationName,
        LocalDateTime windowStartAt,
        LocalDateTime scheduledAt,
        LocalDateTime deadlineAt,
        LocalDateTime takenAt,
        MedicationAnalysisStatus status,
        Integer allowedEarlyMinutes,
        Integer allowedDelayMinutes,
        String detailMessage
) {
}
