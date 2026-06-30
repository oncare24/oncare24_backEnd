package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 봉지(시각 단위). 같은 groupId·시각의 성분들을 한 묶음으로. */
public record MedicationPacketResponse(
        @Schema(description = "복약 예정 시각", example = "08:00:00")
        LocalTime scheduledTime,
        @Schema(description = "시각 라벨(아침약 등). 없으면 null", example = "아침약")
        String label,
        @Schema(description = "복약 일정 유형", example = "DAILY")
        MedicationScheduleType scheduleType,
        @Schema(description = "주간 반복 요일 목록(DAILY면 빈 배열)")
        List<DayOfWeek> daysOfWeek,
        @Schema(description = "복약 시작일(기간 약). 없으면 null", example = "2026-06-01")
        LocalDate startDate,
        @Schema(description = "복약 종료일(기간 약). 없으면 null", example = "2026-06-30")
        LocalDate endDate,
        @Schema(description = "활성화 여부", example = "true")
        boolean active,
        @Schema(description = "봉지 안의 성분/약 목록")
        List<MedicationItemResponse> items
) {
}
