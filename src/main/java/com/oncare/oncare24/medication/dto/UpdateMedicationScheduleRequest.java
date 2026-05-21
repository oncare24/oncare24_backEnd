package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record UpdateMedicationScheduleRequest(
        @NotBlank
        @Size(max = 100)
        @Schema(description = "복약명", example = "혈압약")
        String medicationName,

        @NotNull
        @Schema(description = "복약 예정 시각", example = "08:00:00")
        LocalTime scheduledTime,

        @NotNull
        @Min(0)
        @Schema(description = "예정 시각보다 일찍 복약해도 인정되는 시간(분)", example = "30")
        Integer allowedEarlyMinutes,

        @NotNull
        @Min(0)
        @Schema(description = "예정 시각 이후 지연 허용 시간(분)", example = "60")
        Integer allowedDelayMinutes,

        @NotNull
        @Schema(description = "복약 일정 유형", example = "DAILY")
        MedicationScheduleType scheduleType,

        @Schema(description = "주 1회 일정의 요일 (단일)", example = "MONDAY")
        DayOfWeek dayOfWeek,

        @Schema(description = "주간 반복 일정의 요일 목록", example = "[\"MONDAY\", \"WEDNESDAY\", \"FRIDAY\"]")
        List<DayOfWeek> daysOfWeek,

        @NotNull
        @Schema(description = "복약 일정 활성화 여부", example = "true")
        Boolean active
) {
    @AssertTrue(message = "dayOfWeek or daysOfWeek is required for WEEKLY schedules.")
    public boolean isValidWeeklyDayOfWeek() {
        return scheduleType != MedicationScheduleType.WEEKLY
                || dayOfWeek != null
                || (daysOfWeek != null && !daysOfWeek.isEmpty());
    }
}