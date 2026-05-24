package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
        @Schema(description = "일찍 복약 인정 시간(분)", example = "30")
        Integer allowedEarlyMinutes,
        @Schema(description = "지연 허용 시간(분)", example = "60")
        Integer allowedDelayMinutes,
        @Schema(description = "복약 일정 유형", example = "DAILY")
        MedicationScheduleType scheduleType,
        @Schema(description = "주 1회 요일", example = "MONDAY")
        DayOfWeek dayOfWeek,
        @Schema(description = "주간 반복 요일 목록")
        List<DayOfWeek> daysOfWeek,
        @Schema(description = "활성화 여부", example = "true")
        boolean active,
        @Schema(description = "마지막 변경 일시", example = "2026-05-13T10:30:00")
        LocalDateTime lastChangedAt,
        @Schema(description = "복약 시작일(기간 약). 없으면 null", example = "2026-05-18")
        LocalDate startDate,   // 추가
        @Schema(description = "복약 종료일(기간 약). 없으면 null", example = "2026-05-22")
        LocalDate endDate      // 추가
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
                scheduleType, dayOfWeek, dayOfWeek == null ? List.of() : List.of(dayOfWeek),
                active, lastChangedAt, null, null);
    }
}