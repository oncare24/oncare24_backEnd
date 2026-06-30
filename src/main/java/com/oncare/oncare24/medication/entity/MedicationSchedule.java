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
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Getter
@Table(
        name = "medication_schedule",
        indexes = {
                @Index(name = "idx_med_schedule_ward_active", columnList = "ward_id, is_active"),
                @Index(name = "idx_med_schedule_ward_time", columnList = "ward_id, scheduled_time"),
                @Index(name = "idx_med_schedule_ward_codef", columnList = "ward_id, codef_key_bidx"),
                @Index(name = "idx_med_schedule_active_end", columnList = "is_active, end_date"),
                @Index(name = "idx_med_schedule_ward_group", columnList = "ward_id, group_id")
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

    @Column(name = "encrypted_activity_log_id")
    private Long encryptedActivityLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ward_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_medication_schedule_ward")
    )
    private User ward;

    @Column(name = "medication_name", length = 100)
    private String medicationName;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @ColumnDefault("10")
    @Column(name = "allowed_early_minutes")
    private Integer allowedEarlyMinutes;

    @ColumnDefault("30")
    @Column(name = "allowed_delay_minutes")
    private Integer allowedDelayMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", length = 20)
    private MedicationScheduleType scheduleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 20)
    private DayOfWeek dayOfWeek;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "end_date")
    private LocalDate endDate;                 // 자동등록 약 종료일(조제일+투약일수-1). 수동 등록은 null

    @Column(name = "codef_key_bidx", length = 64)
    private String codefKeyBidx;               // HMAC(처방번호_약품코드) 중복 방지용 블라인드 인덱스

    @Column(name = "group_id", length = 100)
    private String groupId;                     // 봉지(DoseGroup) 식별자. AUTO=codef:..., MANUAL=manual:uuid

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 10)
    private MedicationSource source;            // AUTO(CODEF) / MANUAL(수동)

    @Builder
    private MedicationSchedule(
            Long wardId,
            Long encryptedActivityLogId,
            String medicationName,
            LocalTime scheduledTime,
            Integer allowedEarlyMinutes,
            Integer allowedDelayMinutes,
            MedicationScheduleType scheduleType,
            DayOfWeek dayOfWeek,
            LocalDate endDate,                 // ← 추가
            String codefKeyBidx,               // ← 추가
            String groupId,                    // ← 봉지
            MedicationSource source            // ← 출처
    ) {
        this.wardId = wardId;
        this.encryptedActivityLogId = encryptedActivityLogId;
        this.medicationName = medicationName;
        this.scheduledTime = scheduledTime;
        this.allowedEarlyMinutes = allowedEarlyMinutes;
        this.allowedDelayMinutes = allowedDelayMinutes;
        this.scheduleType = scheduleType;
        this.dayOfWeek = dayOfWeek;
        this.endDate = endDate;                // ← 추가
        this.codefKeyBidx = codefKeyBidx;      // ← 추가
        this.groupId = groupId;
        this.source = source;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void updateEndDate(java.time.LocalDate endDate) {
        this.endDate = endDate;
    }

    /** 봉지 시각 이동(4-3)·생성 시점 시각 확정용. 평문 컬럼 갱신. */
    public void updateScheduledTime(LocalTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    /** 봉지 식별자/출처 보정(마이그레이션·생성 시). */
    public void assignGroup(String groupId, MedicationSource source) {
        this.groupId = groupId;
        this.source = source;
    }

    public void linkEncryptedActivityLog(Long encryptedActivityLogId) {
        this.encryptedActivityLogId = encryptedActivityLogId;
    }
}
