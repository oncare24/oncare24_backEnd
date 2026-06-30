package com.oncare.oncare24.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 오늘의 약 슬롯 안의 성분별 복용 상태. */
public record TodayMedicationItemResponse(
        @Schema(description = "복약 일정 ID", example = "101")
        Long scheduleId,
        @Schema(description = "약/성분 이름", example = "암로디핀 5mg")
        String name,
        @Schema(description = "복용 완료 여부", example = "true")
        boolean taken,
        @Schema(description = "복용 시각. 미복용이면 null", example = "2026-06-30T08:05:00")
        LocalDateTime takenAt
) {
}
