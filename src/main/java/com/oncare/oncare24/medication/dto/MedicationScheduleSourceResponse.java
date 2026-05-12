package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record MedicationScheduleSourceResponse(
        Long scheduleId,
        String medicationName,
        LocalTime scheduledTime,
        Integer allowedEarlyMinutes,
        Integer allowedDelayMinutes,
        MedicationScheduleType scheduleType,
        DayOfWeek dayOfWeek,
        List<DayOfWeek> daysOfWeek,
        boolean active,
        LocalDateTime lastChangedAt
) {
    public MedicationScheduleSourceResponse(
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
        this(scheduleId, medicationName, scheduledTime, allowedEarlyMinutes, allowedDelayMinutes,
                scheduleType, dayOfWeek, dayOfWeek == null ? List.of() : List.of(dayOfWeek), active, lastChangedAt);
    }
}
