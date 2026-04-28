package com.oncare.oncare24.location.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 위치 보고 로그.
 * <p>
 * 30분 주기로 한 사용자당 한 줄씩 쌓임. 한 달이면 약 1,440 row, 부담 적음.
 * <p>
 * <b>설계 포인트</b>
 * <ul>
 *     <li>userId 단순 컬럼 (FK는 안 거는 것이 SafetyZone과 일치)</li>
 *     <li>좌표 DECIMAL(10,7) — 약 1.1cm 정밀도, GPS로 충분</li>
 *     <li>accuracy: 미터 단위. 100m 초과는 서비스 레이어에서 silent drop</li>
 *     <li>(user_id, created_at DESC) 인덱스 — "마지막 위치" 조회와 "최근 보고 시각" 조회 둘 다 커버</li>
 * </ul>
 */
@Entity
@Getter
@Table(
        name = "location_reports",
        indexes = {
                @Index(name = "idx_loc_reports_user_created", columnList = "user_id, created_at DESC")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    /** GPS 정확도 (미터). 작을수록 정확. 100 초과면 서비스에서 거름. */
    @Column(name = "accuracy", nullable = false)
    private Double accuracy;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_source", nullable = false, length = 30)
    private LocationReportSource reportSource;

    @Builder
    private LocationReport(
            Long userId,
            BigDecimal latitude,
            BigDecimal longitude,
            Double accuracy,
            LocationReportSource reportSource
    ) {
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.reportSource = reportSource;
    }
}