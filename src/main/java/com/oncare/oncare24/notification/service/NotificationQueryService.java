package com.oncare.oncare24.notification.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.notification.dto.NotificationResponse;
import com.oncare.oncare24.notification.dto.UnreadCountResponse;
import com.oncare.oncare24.notification.entity.NotificationHistory;
import com.oncare.oncare24.notification.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 조회/읽음 처리 도메인 서비스.
 * <p>
 * <b>왜 NotificationService와 분리</b>
 * <ul>
 *     <li>NotificationService = 발행(write) 책임 — GeofencingService/DeviceStatusService에서 호출</li>
 *     <li>NotificationQueryService = 조회/읽음(read + read_at write) 책임 — Controller에서 호출</li>
 * </ul>
 * 의존성 그래프 단방향 유지를 위해 분리. 후속 Step에서 발행 로직이 더 복잡해져도 Query 쪽은 영향 없음.
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> findMyNotifications(Long currentUserId) {
        return historyRepository.findByRecipientIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse countMyUnread(Long currentUserId) {
        long count = historyRepository.countByRecipientIdAndReadAtIsNull(currentUserId);
        return new UnreadCountResponse(count);
    }

    /**
     * 알림을 읽음 처리. 본인 수신 알림만 가능.
     * <p>
     * 이미 읽은 알림에 대해 다시 호출되어도 idempotent (entity 메서드에서 첫 호출만 반영).
     * 읽음 처리 후 EscalationService 배치는 자연스럽게 이 알림을 후보에서 제외.
     */
    @Transactional
    public NotificationResponse markAsRead(Long currentUserId, Long notificationId) {
        NotificationHistory history = historyRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!history.isOwnedBy(currentUserId)) {
            throw new CustomException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        history.markRead(LocalDateTime.now());
        return NotificationResponse.from(history);
    }
}