package com.oncare.oncare24.notification.repository;

import com.oncare.oncare24.notification.entity.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    /** 보호자 알림 목록. 최신순. 일단 전체 반환하고 프론트에서 필터링/페이징 (Step 8 골격). */
    List<NotificationHistory> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    /** 보호자 안 읽은 알림 개수 (배너/뱃지용). */
    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    /**
     * 에스컬레이션 배치 대상 검색.
     * 조건: read_at IS NULL  AND  sms_sent_at IS NULL  AND  fcm_sent_at < threshold
     * → FCM은 보냈지만 10분 넘게 안 읽었고 SMS도 아직 안 보낸 알림
     */
    @Query("""
        SELECT n FROM NotificationHistory n
        WHERE n.readAt IS NULL
          AND n.smsSentAt IS NULL
          AND n.fcmSentAt IS NOT NULL
          AND n.fcmSentAt < :threshold
        """)
    List<NotificationHistory> findEscalationCandidates(@Param("threshold") LocalDateTime threshold);
}