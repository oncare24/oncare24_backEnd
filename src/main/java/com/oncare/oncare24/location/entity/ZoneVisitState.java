package com.oncare.oncare24.location.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 안전구역별 출입 상태.
 * <p>
 * (ward_id, zone_id) 짝마다 정확히 한 row. 위치 보고 들어올 때마다 상태 머신 갱신.
 * <p>
 * <b>이탈 알림 트리거 조건</b>
 * <pre>
 *   직전 state == INSIDE  &&  현재 판정 == OUTSIDE  →  알림 1회
 *   직전 state == UNKNOWN/OUTSIDE  &&  현재 판정 == OUTSIDE  →  알림 X (이미 밖이거나 첫 보고)
 *   직전 state == OUTSIDE  &&  현재 판정 == INSIDE  →  알림 X (들어옴은 굳이 안 알림)
 * </pre>
 *
 * 이게 메모리에서 결정한 "OS 표준 지오펜싱 패턴"의 서버 구현부.
 */
@Entity
@Getter
@Table(
        name = "zone_visit_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_zvs_ward_zone", columnNames = {"ward_id", "zone_id"})
        },
        indexes = {
                @Index(name = "idx_zvs_ward", columnList = "ward_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneVisitState extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ZoneState state;

    /** 마지막 INSIDE 시각. SafetyZone의 lastVisitedMinutesAgo 계산용. */
    @Column(name = "last_inside_at")
    private LocalDateTime lastInsideAt;

    @Column(name = "state_changed_at", nullable = false)
    private LocalDateTime stateChangedAt;

    @Builder
    private ZoneVisitState(Long wardId, Long zoneId, LocalDateTime now) {
        this.wardId = wardId;
        this.zoneId = zoneId;
        this.state = ZoneState.UNKNOWN;
        this.stateChangedAt = now;
    }

    // === 비즈니스 메서드 ===

    /**
     * 새 판정으로 상태 전이. 알림을 보내야 하는 전이인지 boolean으로 반환.
     * <p>
     * @return true이면 INSIDE → OUTSIDE 전이라서 호출자가 이탈 알림을 발송해야 함.
     */
    public boolean transitionTo(ZoneState newState, LocalDateTime now) {
        ZoneState previous = this.state;

        // INSIDE 진입 시 lastInsideAt 갱신 (계속 INSIDE인 경우도)
        if (newState == ZoneState.INSIDE) {
            this.lastInsideAt = now;
        }

        // 상태 그대로면 stateChangedAt 갱신 X, 알림 X
        if (previous == newState) {
            return false;
        }

        this.state = newState;
        this.stateChangedAt = now;

        // 알림 트리거: INSIDE → OUTSIDE 전이만
        return previous == ZoneState.INSIDE && newState == ZoneState.OUTSIDE;
    }
}