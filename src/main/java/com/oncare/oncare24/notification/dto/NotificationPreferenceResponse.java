package com.oncare.oncare24.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.oncare.oncare24.notification.entity.GuardianNotificationPreference;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

public record NotificationPreferenceResponse(
        @Schema(description = "약 빼먹음 즉시 알림 받기", example = "true")
        boolean immediateMedicationAlert,

        @Schema(description = "저녁 미복용 요약 알림 받기", example = "true")
        boolean dailyDigestEnabled,

        @Schema(description = "요약 알림 받을 시각 (HH:mm)", example = "22:00")
        @JsonFormat(pattern = "HH:mm")
        LocalTime dailyDigestTime
) {
    public static NotificationPreferenceResponse from(GuardianNotificationPreference p) {
        return new NotificationPreferenceResponse(
                p.isImmediateMedicationAlert(),
                p.isDailyDigestEnabled(),
                p.getDailyDigestTime()
        );
    }
}