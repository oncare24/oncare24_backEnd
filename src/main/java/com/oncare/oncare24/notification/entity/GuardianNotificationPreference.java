package com.oncare.oncare24.notification.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 보호자별 알림 수신 설정.
 * <p>
 * <b>설계 결정</b>
 * <ul>
 *     <li>보호자 1명당 1행 (UNIQUE guardian_id). 행이 없으면 default ON으로 간주 (안전 기본값).</li>
 *     <li>지금은 약 관련 토글 2개만. 다른 알림(SOS, 안전구역 등)은 항상 발송 — 보호자가 끌 수 없음(생명 직결).</li>
 *     <li>SOS/안전구역까지 토글 가능하게 만들면 위험. 약은 fatigue 우려가 커서 토글 허용.</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "guardian_notification_preference",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_gnp_guardian",
                        columnNames = "guardian_id"
                )
        }
)
public class GuardianNotificationPreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;

    /** 약 빼먹음 즉시 알림 받기 (default ON) */
    @ColumnDefault("true")
    @Column(name = "immediate_medication_alert", nullable = false)
    private boolean immediateMedicationAlert;

    /** 매일 저녁 미복용 요약 받기 (default ON) */
    @ColumnDefault("true")
    @Column(name = "daily_digest_enabled", nullable = false)
    private boolean dailyDigestEnabled;

    /** 오늘 다이제스트 발송 처리한 시각. null이면 아직 미발송. 같은 날 중복 발송 방지용. */
    @Column(name = "last_digest_sent_at")
    private LocalDateTime lastDigestSentAt;

    /** 요약 받을 시각 (default 22:00) */
    @Column(name = "daily_digest_time", nullable = false)
    private LocalTime dailyDigestTime;

    @Builder
    private GuardianNotificationPreference(
            Long guardianId,
            boolean immediateMedicationAlert,
            boolean dailyDigestEnabled,
            LocalTime dailyDigestTime
    ) {
        this.guardianId = guardianId;
        this.immediateMedicationAlert = immediateMedicationAlert;
        this.dailyDigestEnabled = dailyDigestEnabled;
        this.dailyDigestTime = dailyDigestTime;
    }

    public void updateImmediateMedicationAlert(boolean value) {
        this.immediateMedicationAlert = value;
    }

    public void updateDailyDigestEnabled(boolean value) {
        this.dailyDigestEnabled = value;
    }

    public void updateDailyDigestTime(LocalTime value) {
        this.dailyDigestTime = value;
    }

    public void markDigestSent(LocalDateTime when) {
        this.lastDigestSentAt = when;
    }
}