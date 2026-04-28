package com.oncare.oncare24.safetyzone.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 안전구역 엔티티.
 * <p>
 * <b>설계 포인트</b>
 * <ul>
 *     <li>wardId, guardianId: User 테이블 외래키이지만 @ManyToOne 대신 단순 Long 컬럼으로 보관.
 *         이유는 안전구역 도메인이 User 엔티티 그래프를 끌고 다닐 일이 거의 없고,
 *         조회 시점에 ward/guardian 정보가 필요하면 그때 별도 조회하는 편이 N+1·LazyInit 이슈에 안전.</li>
 *     <li>좌표는 DECIMAL(10,7) — 소수점 7자리면 약 1.1cm 정밀도, GPS로 충분.</li>
 *     <li>radius: 미터 단위. 도메인 정책 200~1000m. Bean Validation은 Request DTO에서.</li>
 *     <li>isActive: soft delete. 삭제 후에도 location_log가 참조할 수 있어야 함.</li>
 *     <li>인덱스 (ward_id, is_active): 5개 제한 카운트 조회 + 활성 zone 목록 조회용.</li>
 * </ul>
 */
@Entity
@Getter
@Table(
        name = "safety_zones",
        indexes = {
                @Index(name = "idx_safety_zones_ward_active", columnList = "ward_id, is_active"),
                @Index(name = "idx_safety_zones_guardian", columnList = "guardian_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyZone extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 이 안전구역의 대상이 되는 피보호자(ELDER) user_id */
    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    /** 이 안전구역을 등록한 보호자(GUARDIAN) user_id */
    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SafetyZoneType type;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "radius", nullable = false)
    private Integer radius;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    /** soft delete 플래그. false가 되면 모든 조회/수정에서 제외. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private SafetyZone(
            Long wardId,
            Long guardianId,
            String name,
            SafetyZoneType type,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radius
    ) {
        this.wardId = wardId;
        this.guardianId = guardianId;
        this.name = name;
        this.type = type;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
        this.notificationEnabled = true; // 등록 직후 기본값 ON
        this.active = true;
    }

    // === 비즈니스 메서드 ===

    /** 위치/이름/타입/반경 한꺼번에 갱신 (수정 화면에서 통째로 보내옴). */
    public void update(
            String name,
            SafetyZoneType type,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radius
    ) {
        this.name = name;
        this.type = type;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    /** 알림 ON/OFF 토글 전용. PATCH 단일 필드 갱신용. */
    public void changeNotificationEnabled(boolean enabled) {
        this.notificationEnabled = enabled;
    }

    /** soft delete. */
    public void softDelete() {
        this.active = false;
    }

    /** 권한 체크 헬퍼. guardianId 본인만 수정/삭제 가능. */
    public boolean isOwnedBy(Long userId) {
        return this.guardianId.equals(userId);
    }
}