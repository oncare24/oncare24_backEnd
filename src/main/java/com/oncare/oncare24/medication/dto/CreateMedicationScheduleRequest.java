package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreateMedicationScheduleRequest(
        @NotNull @Schema(description = "피보호자 ID", example = "2") Long wardId,
        @NotBlank @Size(max = 100) @Schema(description = "복약명", example = "혈압약") String medicationName,
        @NotNull @Schema(description = "복약 예정 시각", example = "08:00:00") LocalTime scheduledTime,
        @Min(0) @Schema(description = "일찍 복약 인정(분)", example = "30") Integer allowedEarlyMinutes,
        @Min(0) @Schema(description = "지연 허용(분)", example = "60") Integer allowedDelayMinutes,
        @NotNull @Schema(description = "복약 일정 유형", example = "DAILY") MedicationScheduleType scheduleType,
        @Schema(description = "주 1회 요일", example = "MONDAY") DayOfWeek dayOfWeek,
        @Schema(description = "주간 반복 요일 목록") List<DayOfWeek> daysOfWeek,
        @Schema(description = "복용 시작일(기간 약). 계속 복용이면 null", example = "2026-05-18") LocalDate startDate,
        @Schema(description = "복용 종료일(기간 약). 계속 복용이면 null", example = "2026-05-22") LocalDate endDate
) {
    public CreateMedicationScheduleRequest(
            Long wardId, String medicationName, LocalTime scheduledTime,
            Integer allowedEarlyMinutes, Integer allowedDelayMinutes,
            MedicationScheduleType scheduleType, DayOfWeek dayOfWeek
    ) {
        this(wardId, medicationName, scheduledTime, allowedEarlyMinutes, allowedDelayMinutes,
                scheduleType, dayOfWeek, null, null, null);
    }

    @AssertTrue(message = "dayOfWeek or daysOfWeek is required for WEEKLY schedules.")
    public boolean isValidWeeklyDaysOfWeek() {
        return scheduleType != MedicationScheduleType.WEEKLY
                || dayOfWeek != null
                || (daysOfWeek != null && !daysOfWeek.isEmpty());
    }

    @AssertTrue(message = "endDate must not be before startDate.")
    public boolean isValidPeriod() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}