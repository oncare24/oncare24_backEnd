package com.oncare.oncare24.medication.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

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
        List<DayOfWeek> daysOfWeek,
        boolean active
) {
    public MedicationSchedulePayload(
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
        this(action, scheduleId, medicationName, scheduledTime, allowedEarlyMinutes, allowedDelayMinutes,
                scheduleType, dayOfWeek, dayOfWeek == null ? List.of() : List.of(dayOfWeek), active);
    }
}
