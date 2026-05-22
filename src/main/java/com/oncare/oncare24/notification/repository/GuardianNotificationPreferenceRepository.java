package com.oncare.oncare24.notification.repository;

import com.oncare.oncare24.notification.entity.GuardianNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 보호자 알림 설정 표 꺼내는 도구.
 */
public interface GuardianNotificationPreferenceRepository
        extends JpaRepository<GuardianNotificationPreference, Long> {

    Optional<GuardianNotificationPreference> findByGuardianId(Long guardianId);

    /**
     * 다이제스트 배치(3번 작업)에서 사용.
     * "다이제스트 ON + 오늘 요약 시각이 이미 지났고 + 오늘 아직 발송 안 한" 보호자 전체 조회.
     * <p>
     * 배치가 늦게 돌아도 (예: 22:00 설정인데 22:03에 배치 실행) 그날 안에 한 번은 발송됨.
     * <p>
     * 자정 이후엔 daily_digest_time이 어떻든 nowTime(00:00 등)보다 크니까 자연 제외됨.
     */
    @Query("SELECT p FROM GuardianNotificationPreference p " +
            "WHERE p.dailyDigestEnabled = true " +
            "AND p.dailyDigestTime <= :nowTime " +
            "AND (p.lastDigestSentAt IS NULL OR p.lastDigestSentAt < :todayStart)")
    List<GuardianNotificationPreference> findPendingDigestRecipients(
            @Param("nowTime") LocalTime nowTime,
            @Param("todayStart") LocalDateTime todayStart
    );

    /**
     * 일일 다이제스트 배치(3번 작업)에서 사용.
     * "지금 시각이 요약 시각인 + 다이제스트 ON 인 보호자 전체" 조회.
     */
    List<GuardianNotificationPreference> findByDailyDigestEnabledTrueAndDailyDigestTime(LocalTime time);
}