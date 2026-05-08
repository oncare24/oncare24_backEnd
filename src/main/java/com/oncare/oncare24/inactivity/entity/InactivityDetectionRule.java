package com.oncare.oncare24.inactivity.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import com.oncare.oncare24.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@Table(
        name = "inactivity_detection_rule",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inactivity_rule_ward", columnNames = "ward_id")
        },
        indexes = {
                @Index(name = "idx_inactivity_rule_ward_active", columnList = "ward_id, is_active")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InactivityDetectionRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ward_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inactivity_detection_rule_ward")
    )
    private User ward;

    @ColumnDefault("240")
    @Column(name = "warning_minutes", nullable = false)
    private Integer warningMinutes;

    @ColumnDefault("480")
    @Column(name = "danger_minutes", nullable = false)
    private Integer dangerMinutes;

    @ColumnDefault("120")
    @Column(name = "stale_location_warning_minutes", nullable = false)
    private Integer staleLocationWarningMinutes;

    @ColumnDefault("360")
    @Column(name = "stale_location_danger_minutes", nullable = false)
    private Integer staleLocationDangerMinutes;

    @ColumnDefault("30")
    @Column(name = "expected_report_interval_minutes", nullable = false)
    private Integer expectedReportIntervalMinutes;

    @ColumnDefault("30")
    @Column(name = "min_movement_meters", nullable = false)
    private Double minMovementMeters;

    @ColumnDefault("100")
    @Column(name = "max_accuracy_meters", nullable = false)
    private Double maxAccuracyMeters;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private InactivityDetectionRule(
            Long wardId,
            Integer warningMinutes,
            Integer dangerMinutes,
            Integer staleLocationWarningMinutes,
            Integer staleLocationDangerMinutes,
            Integer expectedReportIntervalMinutes,
            Double minMovementMeters,
            Double maxAccuracyMeters
    ) {
        this.wardId = wardId;
        this.warningMinutes = warningMinutes != null ? warningMinutes : 240;
        this.dangerMinutes = dangerMinutes != null ? dangerMinutes : 480;
        this.staleLocationWarningMinutes = staleLocationWarningMinutes != null ? staleLocationWarningMinutes : 120;
        this.staleLocationDangerMinutes = staleLocationDangerMinutes != null ? staleLocationDangerMinutes : 360;
        this.expectedReportIntervalMinutes = expectedReportIntervalMinutes != null ? expectedReportIntervalMinutes : 30;
        this.minMovementMeters = minMovementMeters != null ? minMovementMeters : 30.0;
        this.maxAccuracyMeters = maxAccuracyMeters != null ? maxAccuracyMeters : 100.0;
        this.active = true;
    }

    public void update(
            Integer warningMinutes,
            Integer dangerMinutes,
            Integer staleLocationWarningMinutes,
            Integer staleLocationDangerMinutes,
            Integer expectedReportIntervalMinutes,
            Double minMovementMeters,
            Double maxAccuracyMeters
    ) {
        this.warningMinutes = warningMinutes != null ? warningMinutes : 240;
        this.dangerMinutes = dangerMinutes != null ? dangerMinutes : 480;
        this.staleLocationWarningMinutes = staleLocationWarningMinutes != null ? staleLocationWarningMinutes : 120;
        this.staleLocationDangerMinutes = staleLocationDangerMinutes != null ? staleLocationDangerMinutes : 360;
        this.expectedReportIntervalMinutes = expectedReportIntervalMinutes != null ? expectedReportIntervalMinutes : 30;
        this.minMovementMeters = minMovementMeters != null ? minMovementMeters : 30.0;
        this.maxAccuracyMeters = maxAccuracyMeters != null ? maxAccuracyMeters : 100.0;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
