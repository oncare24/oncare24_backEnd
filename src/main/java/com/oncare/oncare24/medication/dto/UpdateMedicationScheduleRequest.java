package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record UpdateMedicationScheduleRequest(
        @NotBlank
        @Size(max = 100)
        String medicationName,

        @NotNull
        LocalTime scheduledTime,

        @NotNull
        @Min(0)
        Integer allowedEarlyMinutes,

        @NotNull
        @Min(0)
        Integer allowedDelayMinutes,

        @NotNull
        MedicationScheduleType scheduleType,

        DayOfWeek dayOfWeek,

        @NotNull
        Boolean active
) {
    @AssertTrue(message = "dayOfWeek is required for WEEKLY schedules.")
    public boolean isValidWeeklyDayOfWeek() {
        return scheduleType != MedicationScheduleType.WEEKLY || dayOfWeek != null;
    }
}
