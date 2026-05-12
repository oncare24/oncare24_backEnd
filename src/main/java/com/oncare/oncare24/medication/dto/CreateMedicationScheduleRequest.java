package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record CreateMedicationScheduleRequest(
        @NotNull
        Long wardId,

        @NotBlank
        @Size(max = 100)
        String medicationName,

        @NotNull
        LocalTime scheduledTime,

        @Min(0)
        Integer allowedEarlyMinutes,

        @Min(0)
        Integer allowedDelayMinutes,

        @NotNull
        MedicationScheduleType scheduleType,

        DayOfWeek dayOfWeek,

        List<DayOfWeek> daysOfWeek
) {
    public CreateMedicationScheduleRequest(
            Long wardId,
            String medicationName,
            LocalTime scheduledTime,
            Integer allowedEarlyMinutes,
            Integer allowedDelayMinutes,
            MedicationScheduleType scheduleType,
            DayOfWeek dayOfWeek
    ) {
        this(wardId, medicationName, scheduledTime, allowedEarlyMinutes, allowedDelayMinutes, scheduleType, dayOfWeek, null);
    }

    @AssertTrue(message = "dayOfWeek or daysOfWeek is required for WEEKLY schedules.")
    public boolean isValidWeeklyDaysOfWeek() {
        return scheduleType != MedicationScheduleType.WEEKLY
                || dayOfWeek != null
                || (daysOfWeek != null && !daysOfWeek.isEmpty());
    }
}
