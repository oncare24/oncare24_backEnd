package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationLogSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateMedicationLogRequest(
        @NotNull
        Long wardId,

        Long scheduleId,

        LocalDateTime takenAt,

        @Size(max = 100)
        String medicationName,

        MedicationLogSource logSource
) {
}
