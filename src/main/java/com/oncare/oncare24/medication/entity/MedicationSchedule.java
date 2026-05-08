package com.oncare.oncare24.medication.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import com.oncare.oncare24.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Getter
@Table(
        name = "medication_schedule",
        indexes = {
                @Index(name = "idx_med_schedule_ward_active", columnList = "ward_id, is_active"),
                @Index(name = "idx_med_schedule_ward_time", columnList = "ward_id, scheduled_time")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationSchedule extends BaseTimeEntity {

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
            foreignKey = @ForeignKey(name = "fk_medication_schedule_ward")
    )
    private User ward;

    @Column(name = "medication_name", nullable = false, length = 100)
    private String medicationName;

    @Column(name = "scheduled_time", nullable = false)
    private LocalTime scheduledTime;

    @ColumnDefault("10")
    @Column(name = "allowed_early_minutes", nullable = false)
    private Integer allowedEarlyMinutes;

    @ColumnDefault("30")
    @Column(name = "allowed_delay_minutes", nullable = false)
    private Integer allowedDelayMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    private MedicationScheduleType scheduleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 20)
    private DayOfWeek dayOfWeek;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private MedicationSchedule(
            Long wardId,
            String medicationName,
            LocalTime scheduledTime,
            Integer allowedEarlyMinutes,
            Integer allowedDelayMinutes,
            MedicationScheduleType scheduleType,
            DayOfWeek dayOfWeek
    ) {
        this.wardId = wardId;
        this.medicationName = medicationName;
        this.scheduledTime = scheduledTime;
        this.allowedEarlyMinutes = allowedEarlyMinutes != null ? allowedEarlyMinutes : 10;
        this.allowedDelayMinutes = allowedDelayMinutes != null ? allowedDelayMinutes : 30;
        this.scheduleType = scheduleType;
        this.dayOfWeek = dayOfWeek;
        this.active = true;
    }

    public void update(
            String medicationName,
            LocalTime scheduledTime,
            Integer allowedEarlyMinutes,
            Integer allowedDelayMinutes,
            MedicationScheduleType scheduleType,
            DayOfWeek dayOfWeek
    ) {
        this.medicationName = medicationName;
        this.scheduledTime = scheduledTime;
        this.allowedEarlyMinutes = allowedEarlyMinutes != null ? allowedEarlyMinutes : 10;
        this.allowedDelayMinutes = allowedDelayMinutes != null ? allowedDelayMinutes : 30;
        this.scheduleType = scheduleType;
        this.dayOfWeek = dayOfWeek;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
