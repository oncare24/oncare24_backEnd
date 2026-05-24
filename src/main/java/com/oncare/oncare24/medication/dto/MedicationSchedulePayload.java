package com.oncare.oncare24.medication.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
        boolean active,
        LocalDate startDate,   // 추가
        LocalDate endDate      // 추가
) {
    // 기존 9-arg 호출부 호환 (start/end 없으면 null)
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
                scheduleType, dayOfWeek, dayOfWeek == null ? List.of() : List.of(dayOfWeek), active, null, null);
    }
}