package com.oncare.oncare24.notification.dto;

import com.oncare.oncare24.notification.entity.NotificationHistory;
import com.oncare.oncare24.notification.entity.NotificationType;

import java.time.LocalDateTime;

/**
 * 알림 단건 응답.
 * <p>
 * 보호자 앱의 알림센터/배너 데이터.
 * read_at이 null이면 미확인 상태로 표시.
 * <p>
 * <b>type별 부가 데이터</b>
 * <ul>
 *     <li>relatedZoneId — ZONE_EXIT 알림 카드 탭 시 해당 zone으로 라우팅</li>
 *     <li>sosEventId — SOS 알림 카드 탭 시 SosLocationView로 라우팅</li>
 * </ul>
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        Long wardId,
        Long relatedZoneId,
        Long sosEventId,
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
                h.getSosEventId(),
                h.getCreatedAt(),
                h.getReadAt()
        );
    }
}