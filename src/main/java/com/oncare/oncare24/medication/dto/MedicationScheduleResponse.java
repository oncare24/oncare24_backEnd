package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record MedicationScheduleResponse(
        Long scheduleId,
        List<Long> scheduleIds,
        Long wardId,
        String medicationName,
        LocalTime scheduledTime,
        Integer allowedEarlyMinutes,
        Integer allowedDelayMinutes,
        MedicationScheduleType scheduleType,
        DayOfWeek dayOfWeek,
        List<DayOfWeek> daysOfWeek,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MedicationScheduleResponse from(MedicationSchedule schedule) {
        return new MedicationScheduleResponse(
                schedule.getId(),
                schedule.getId() == null ? List.of() : List.of(schedule.getId()),
                schedule.getWardId(),
                schedule.getMedicationName(),
                schedule.getScheduledTime(),
                schedule.getAllowedEarlyMinutes(),
                schedule.getAllowedDelayMinutes(),
                schedule.getScheduleType(),
                schedule.getDayOfWeek(),
                schedule.getDayOfWeek() == null ? List.of() : List.of(schedule.getDayOfWeek()),
                schedule.isActive(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }

    public static MedicationScheduleResponse from(MedicationSchedule schedule, MedicationSchedulePayload payload) {
        return new MedicationScheduleResponse(
                schedule.getId(),
                schedule.getId() == null ? List.of() : List.of(schedule.getId()),
                schedule.getWardId(),
                payload.medicationName(),
                payload.scheduledTime(),
                payload.allowedEarlyMinutes(),
                payload.allowedDelayMinutes(),
                payload.scheduleType(),
                payload.dayOfWeek(),
                normalizeDays(payload.dayOfWeek(), payload.daysOfWeek()),
                schedule.isActive(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }

    public static MedicationScheduleResponse from(
            MedicationSchedule firstSchedule,
            MedicationSchedulePayload firstPayload,
            List<Long> scheduleIds,
            List<DayOfWeek> daysOfWeek
    ) {
        return new MedicationScheduleResponse(
                firstSchedule.getId(),
                scheduleIds,
                firstSchedule.getWardId(),
                firstPayload.medicationName(),
                firstPayload.scheduledTime(),
                firstPayload.allowedEarlyMinutes(),
                firstPayload.allowedDelayMinutes(),
                firstPayload.scheduleType(),
                firstPayload.dayOfWeek(),
                daysOfWeek,
                firstSchedule.isActive(),
                firstSchedule.getCreatedAt(),
                firstSchedule.getUpdatedAt()
        );
    }

    private static List<DayOfWeek> normalizeDays(DayOfWeek dayOfWeek, List<DayOfWeek> daysOfWeek) {
        if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
            return daysOfWeek;
        }
        return dayOfWeek == null ? List.of() : List.of(dayOfWeek);
    }
}
