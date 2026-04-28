package com.oncare.oncare24.notification.dto;

import com.oncare.oncare24.notification.entity.NotificationHistory;
import com.oncare.oncare24.notification.entity.NotificationType;

import java.time.LocalDateTime;

/**
 * 알림 단건 응답.
 * <p>
 * 보호자 앱의 알림센터/배너 데이터.
 * read_at이 null이면 미확인 상태로 표시.
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        Long wardId,
        Long relatedZoneId,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
    public static NotificationResponse from(NotificationHistory h) {
        return new NotificationResponse(
                h.getId(),
                h.getType(),
                h.getTitle(),
                h.getBody(),
                h.getWardId(),
                h.getRelatedZoneId(),
                h.getCreatedAt(),
                h.getReadAt()
        );
    }
}