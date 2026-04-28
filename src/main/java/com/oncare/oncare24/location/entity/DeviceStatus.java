package com.oncare.oncare24.location.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 단말 상태 머신.
 * <p>
 * 한 사용자당 1행 (uk_device_status_user UNIQUE). 회원가입 직후 NEVER_CONNECTED로 생성.
 * <p>
 * <b>상태 전이</b>
 * <ul>
 *     <li>NEVER_CONNECTED → ACTIVE: 첫 위치 보고 도착 시</li>
 *     <li>ACTIVE → DISCONNECTED: 30분간 보고 없으면 5분 배치가 전환 + 보호자 1회 알림</li>
 *     <li>DISCONNECTED → ACTIVE: 보고가 다시 들어오면 즉시 복귀 (복귀 알림은 보내지 않음 — 노이즈)</li>
 * </ul>
 *
 * <b>왜 location_reports 테이블에서 lastReportAt을 매번 계산하지 않는가</b>
 * <p>
 * 빈번한 배치 UPDATE를 user 테이블에서 격리하기 위함 (메모리 #ERD 결정).
 * 또 "DISCONNECTED 알림을 한 번만 보냈는지" 같은 상태 정보를 따로 들고 있어야 해서 별도 테이블이 자연스럽다.
 */
@Entity
@Getter
@Table(
        name = "device_status",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_status_user", columnNames = "user_id")
        },
        indexes = {
                @Index(name = "idx_device_status_state_report", columnList = "state, last_report_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceStatus extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private DeviceState state;

    /** 마지막 위치 보고가 들어온 시각. NEVER_CONNECTED일 때만 null. */
    @Column(name = "last_report_at")
    private LocalDateTime lastReportAt;

    /** DISCONNECTED 알림이 이미 발송됐는지. ACTIVE 복귀 시 false로 리셋. */
    @Column(name = "disconnect_notified", nullable = false)
    private boolean disconnectNotified;

    @Builder
    private DeviceStatus(Long userId) {
        this.userId = userId;
        this.state = DeviceState.NEVER_CONNECTED;
        this.lastReportAt = null;
        this.disconnectNotified = false;
    }

    // === 비즈니스 메서드 ===

    /** 위치 보고가 들어왔을 때 호출. 어떤 상태였든 ACTIVE로 전환. */
    public void onLocationReported(LocalDateTime now) {
        this.state = DeviceState.ACTIVE;
        this.lastReportAt = now;
        this.disconnectNotified = false; // 복귀 시 알림 플래그 리셋
    }

    /** 5분 배치에서 30분 미수신 감지 시 호출. */
    public void markDisconnected() {
        this.state = DeviceState.DISCONNECTED;
    }

    /** 보호자에게 DISCONNECTED 알림을 보낸 직후 호출. 중복 방지용. */
    public void markDisconnectNotified() {
        this.disconnectNotified = true;
    }

    public boolean isActive() {
        return this.state == DeviceState.ACTIVE;
    }

    public boolean isDisconnectAlreadyNotified() {
        return this.disconnectNotified;
    }
}