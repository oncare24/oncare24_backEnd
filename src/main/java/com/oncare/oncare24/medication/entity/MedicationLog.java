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

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "medication_log",
        indexes = {
                @Index(name = "idx_med_log_ward_taken", columnList = "ward_id, taken_at DESC"),
                @Index(name = "idx_med_log_schedule", columnList = "schedule_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationLog extends BaseTimeEntity {

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
            foreignKey = @ForeignKey(name = "fk_medication_log_ward")
    )
    private User ward;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "encrypted_activity_log_id")
    private Long encryptedActivityLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "schedule_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_medication_log_schedule")
    )
    private MedicationSchedule schedule;

    @Column(name = "taken_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime takenAt;

    @Column(name = "medication_name", length = 100)
    private String medicationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_source", length = 30)
    private MedicationLogSource logSource;

    @Builder
    private MedicationLog(
            Long wardId,
            Long scheduleId,
            Long encryptedActivityLogId,
            LocalDateTime takenAt,
            String medicationName,
            MedicationLogSource logSource
    ) {
        this.wardId = wardId;
        this.scheduleId = scheduleId;
        this.encryptedActivityLogId = encryptedActivityLogId;
        this.takenAt = takenAt;
        this.medicationName = medicationName;
        this.logSource = logSource;
    }

    public void linkEncryptedActivityLog(Long encryptedActivityLogId) {
        this.encryptedActivityLogId = encryptedActivityLogId;
    }
}
