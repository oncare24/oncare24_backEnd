package com.oncare.oncare24.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 4-2 오늘의 약 응답: { "slots": [...] }. */
public record TodayMedicationResponse(
        @Schema(description = "시각(슬롯) 목록")
        List<TodayMedicationSlotResponse> slots
) {
}
