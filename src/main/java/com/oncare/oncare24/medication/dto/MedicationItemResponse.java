package com.oncare.oncare24.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 봉지(packet) 안의 개별 성분/약. */
public record MedicationItemResponse(
        @Schema(description = "복약 일정 ID", example = "101")
        Long scheduleId,
        @Schema(description = "약/성분 이름", example = "암로디핀 5mg")
        String name
) {
}
