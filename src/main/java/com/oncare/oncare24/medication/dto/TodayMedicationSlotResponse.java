package com.oncare.oncare24.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;
import java.util.List;

/** 오늘의 약 — 시각(슬롯) 단위 + 성분별 상태. */
public record TodayMedicationSlotResponse(
        @Schema(description = "복약 예정 시각", example = "08:00:00")
        LocalTime scheduledTime,
        @Schema(description = "시각 라벨(아침약 등). 없으면 null", example = "아침약")
        String label,
        @Schema(description = "슬롯의 모든 성분 복용 완료 여부", example = "false")
        boolean allTaken,
        @Schema(description = "성분별 복용 상태")
        List<TodayMedicationItemResponse> items
) {
}
