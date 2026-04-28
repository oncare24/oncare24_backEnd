package com.oncare.oncare24.notification.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 발송/확인 이력.
 * <p>
 * <b>역할 3가지</b>
 * <ol>
 *     <li>발송 이력 추적 (FCM/SMS 성공 여부 기록)</li>
 *     <li>에스컬레이션 큐 (10분 미확인 → SMS 폴백 대상 조회)</li>
 *     <li>보호자 앱 내 배너/알림센터 데이터 소스 (read_at 기준)</li>
 * </ol>
 *
 * <b>읽음 처리</b>
 * <p>
 * 보호자가 앱에서 알림을 탭하면 read_at 기록. 이 시점부터 SMS 에스컬레이션 대상에서 제외.
 *
 * <b>인덱스 두 개</b>
 * <ul>
 *     <li>(recipient_id, read_at): 보호자 알림 목록 조회 (안 읽은 것 우선)</li>
 *     <li>(read_at, sms_sent_at, fcm_sent_at): 에스컬레이션 배치 후보 검색</li>
 * </ul>
 */
@Entity
@Getter
@Table(
        name = "notification_history",
        indexes = {
                @Index(name = "idx_nh_recipient_read", columnList = "recipient_id, read_at"),
                @Index(name = "idx_nh_escalation", columnList = "read_at, sms_sent_at, fcm_sent_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 알림 받는 사람 (보통 보호자). */
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    /** 관련 피보호자 (있다면). ZONE_EXIT/DEVICE_DISCONNECTED는 항상 채움. */
    @Column(name = "ward_id")
    private Long wardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "body", nullable = false, length = 500)
    private String body;

    /** ZONE_EXIT일 때 어느 zone인지. 다른 type은 null. */
    @Column(name = "related_zone_id")
    private Long relatedZoneId;

    @Column(name = "fcm_sent_at")
    private LocalDateTime fcmSentAt;

    /** null=미시도, true=Google 서버 전달 성공, false=실패. */
    @Column(name = "fcm_success")
    private Boolean fcmSuccess;

    @Column(name = "sms_sent_at")
    private LocalDateTime smsSentAt;

    @Column(name = "sms_success")
    private Boolean smsSuccess;

    /** 보호자가 앱에서 알림을 탭한 시각. null이면 미확인. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    private NotificationHistory(
            Long recipientId,
            Long wardId,
            NotificationType type,
            String title,
            String body,
            Long relatedZoneId
    ) {
        this.recipientId = recipientId;
        this.wardId = wardId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.relatedZoneId = relatedZoneId;
    }

    // === 비즈니스 메서드 ===

    public void markFcmSent(boolean success, LocalDateTime now) {
        this.fcmSentAt = now;
        this.fcmSuccess = success;
    }

    public void markSmsSent(boolean success, LocalDateTime now) {
        this.smsSentAt = now;
        this.smsSuccess = success;
    }

    public void markRead(LocalDateTime now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return this.recipientId.equals(userId);
    }

    public boolean isRead() {
        return this.readAt != null;
    }
}