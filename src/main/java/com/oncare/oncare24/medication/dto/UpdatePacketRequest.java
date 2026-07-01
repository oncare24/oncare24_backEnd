package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** 4-4 봉지 속성(요일/기간) 변경 요청. */
public record UpdatePacketRequest(
        @Schema(description = "복약 일정 유형", example = "WEEKLY")
        @NotNull MedicationScheduleType scheduleType,
        @Schema(description = "주간 반복 요일 목록(DAILY면 빈 배열/생략)")
        List<DayOfWeek> daysOfWeek,
        @Schema(description = "복약 시작일(기간 약). 없으면 null", example = "2026-06-01")
        LocalDate startDate,
        @Schema(description = "복약 종료일(기간 약). 없으면 null", example = "2026-06-30")
        LocalDate endDate
) {
}
