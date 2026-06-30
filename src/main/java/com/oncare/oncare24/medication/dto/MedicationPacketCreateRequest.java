package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 4-5 봉지 생성 시 개별 시각(packet). */
public record MedicationPacketCreateRequest(
        @Schema(description = "복약 예정 시각", example = "08:00:00")
        @NotNull LocalTime scheduledTime,
        @Schema(description = "복약 일정 유형", example = "DAILY")
        @NotNull MedicationScheduleType scheduleType,
        @Schema(description = "주간 반복 요일 목록(DAILY면 빈 배열/생략)")
        List<DayOfWeek> daysOfWeek,
        @Schema(description = "복약 시작일(기간 약). 없으면 null")
        LocalDate startDate,
        @Schema(description = "복약 종료일(기간 약). 없으면 null")
        LocalDate endDate,
        @Schema(description = "일찍 복약 인정 시간(분). 없으면 기본 10")
        Integer allowedEarlyMinutes,
        @Schema(description = "지연 허용 시간(분). 없으면 기본 30")
        Integer allowedDelayMinutes
) {
}
