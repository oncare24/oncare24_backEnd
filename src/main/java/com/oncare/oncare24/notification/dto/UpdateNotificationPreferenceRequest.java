package com.oncare.oncare24.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

/**
 * 알림 설정 부분 업데이트. null 필드는 기존값 유지.
 * <p>
 * 프론트 토글 1개 끄기 = immediateMedicationAlert 만 보냄. 나머지는 null.
 */
public record UpdateNotificationPreferenceRequest(
        @Schema(description = "약 빼먹음 즉시 알림 (null이면 변경 안 함)", example = "true")
        Boolean immediateMedicationAlert,

        @Schema(description = "저녁 요약 알림 (null이면 변경 안 함)", example = "false")
        Boolean dailyDigestEnabled,

        @Schema(description = "요약 시각 HH:mm (null이면 변경 안 함)", example = "21:30")
        @JsonFormat(pattern = "HH:mm")
        LocalTime dailyDigestTime
) {}