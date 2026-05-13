package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record MedicationScheduleResponse(
        @Schema(description = "대표 복약 일정 ID", example = "15")
        Long scheduleId,
        @Schema(description = "함께 생성된 복약 일정 ID 목록", example = "[15, 16, 17]")
        List<Long> scheduleIds,
        @Schema(description = "피보호자 ID", example = "2")
        Long wardId,
        @Schema(description = "복약명", example = "혈압약")
        String medicationName,
        @Schema(description = "복약 예정 시각", example = "08:00:00")
        LocalTime scheduledTime,
        @Schema(description = "예정 시각보다 일찍 복약해도 인정되는 시간(분)", example = "30")
        Integer allowedEarlyMinutes,
        @Schema(description = "예정 시각 이후 지연 허용 시간(분)", example = "60")
        Integer allowedDelayMinutes,
        @Schema(description = "복약 일정 유형", example = "DAILY")
        MedicationScheduleType scheduleType,
        @Schema(description = "주 1회 일정의 요일", example = "MONDAY")
        DayOfWeek dayOfWeek,
        @Schema(description = "주간 반복 일정의 요일 목록", example = "[\"MONDAY\", \"WEDNESDAY\", \"FRIDAY\"]")
        List<DayOfWeek> daysOfWeek,
        @Schema(description = "복약 일정 활성화 여부", example = "true")
        boolean active,
        @Schema(description = "복약 일정 생성 일시", example = "2026-05-13T10:00:00")
        LocalDateTime createdAt,
        @Schema(description = "복약 일정 수정 일시", example = "2026-05-13T10:30:00")
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
