package com.oncare.oncare24.safetyzone.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 안전구역 알림 ON/OFF 토글 전용 요청.
 * <p>
 * 알림 토글은 빈번하게 일어나므로(피보호자가 병원 갈 때만 끔 등),
 * 전체 수정 PUT과 분리해 PATCH 단일 필드로 처리.
 */
public record UpdateNotificationRequest(
        @NotNull(message = "알림 설정 값이 필요해요")
        Boolean enabled
) {
}