package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MedicationScheduleResponse(
        Long scheduleId,
        Long wardId,
        String medicationName,
        LocalTime scheduledTime,
        Integer allowedEarlyMinutes,
        Integer allowedDelayMinutes,
        MedicationScheduleType scheduleType,
        DayOfWeek dayOfWeek,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MedicationScheduleResponse from(MedicationSchedule schedule) {
        return new MedicationScheduleResponse(
                schedule.getId(),
                schedule.getWardId(),
                schedule.getMedicationName(),
                schedule.getScheduledTime(),
                schedule.getAllowedEarlyMinutes(),
                schedule.getAllowedDelayMinutes(),
                schedule.getScheduleType(),
                schedule.getDayOfWeek(),
                schedule.isActive(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }
}
