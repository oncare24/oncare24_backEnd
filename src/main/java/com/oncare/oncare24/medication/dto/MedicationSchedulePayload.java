package com.oncare.oncare24.medication.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;

public record MedicationSchedulePayload(
        String action,
        Long scheduleId,
        String medicationName,
        LocalTime scheduledTime,
        Integer allowedEarlyMinutes,
        Integer allowedDelayMinutes,
        MedicationScheduleType scheduleType,
        DayOfWeek dayOfWeek,
        boolean active
) {
}
