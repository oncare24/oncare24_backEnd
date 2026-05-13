package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record MedicationScheduleSourceResponse(
        @Schema(description = "복약 일정 ID", example = "15")
        Long scheduleId,
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
        @Schema(description = "암호화 로그 기준 마지막 변경 일시", example = "2026-05-13T10:30:00")
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
