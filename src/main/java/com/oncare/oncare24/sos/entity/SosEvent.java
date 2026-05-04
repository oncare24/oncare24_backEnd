package com.oncare.oncare24.sos.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SOS 긴급 호출 이벤트.
 * <p>
 * <b>역할 3가지</b>
 * <ol>
 *     <li>호출 이력 추적 (언제, 어디서, 누가 호출했는지)</li>
 *     <li>호출 시점의 위치 스냅샷 보존 (location_reports와 별개로 immutable 기록)</li>
 *     <li>발송 결과 집계 (몇 명의 보호자에게 알림이 갔는지)</li>
 * </ol>
 *
 * <b>왜 location_reports와 별개로 좌표를 직접 보관하나</b>
 * <ul>
 *     <li>SOS는 호출 시점의 정확한 위치가 사후 추적·보고에 결정적</li>
 *     <li>location_reports는 30분 주기라 호출 직전 위치가 멀 수 있음</li>
 *     <li>호출 시 클라이언트가 실시간 GPS를 함께 보내면 그 값을 우선 저장</li>
 * </ul>
 *
 * <b>인덱스</b>
 * <ul>
 *     <li>(ward_id, created_at DESC): 피보호자별 최근 SOS 이력 조회</li>
 * </ul>
 */
@Entity
@Getter
@Table(
        name = "sos_events",
        indexes = {
                @Index(name = "idx_sos_ward_created", columnList = "ward_id, created_at DESC")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SosEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 호출자 (피보호자). */
    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    /** 호출 시점 위도. 클라이언트가 보낸 실시간 GPS 우선, 없으면 location_reports 최신값 폴백. */
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /** 위치 출처. CLIENT=호출 시 함께 전송, FALLBACK=location_reports에서 가져옴, NONE=둘 다 없음. */
    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", nullable = false, length = 20)
    private SosLocationSource locationSource;

    /** 알림 발송된 보호자 수 (이 호출에 대해 NotificationHistory가 만들어진 row 수). */
    @Column(name = "notified_guardian_count", nullable = false)
    private int notifiedGuardianCount;

    @Builder
    private SosEvent(
            Long wardId,
            BigDecimal latitude,
            BigDecimal longitude,
            SosLocationSource locationSource
    ) {
        this.wardId = wardId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationSource = locationSource;
        this.notifiedGuardianCount = 0;
    }

    public void markNotified(int guardianCount) {
        this.notifiedGuardianCount = guardianCount;
    }

    /**
     * 위치 정보가 위도·경도 둘 다 있는지.
     * 알림 본문에 좌표를 포함할지 판단할 때 사용.
     */
    public boolean hasLocation() {
        return this.latitude != null && this.longitude != null;
    }
}