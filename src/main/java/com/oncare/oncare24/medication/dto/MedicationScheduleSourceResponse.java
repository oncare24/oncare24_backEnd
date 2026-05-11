package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MedicationScheduleSourceResponse(
        Long scheduleId,
        String medicationName,
        LocalTime scheduledTime,
        Integer allowedEarlyMinutes,
        Integer allowedDelayMinutes,
        MedicationScheduleType scheduleType,
        DayOfWeek dayOfWeek,
        boolean active,
        LocalDateTime lastChangedAt
) {
}
