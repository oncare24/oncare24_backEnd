package com.oncare.oncare24.medication.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 약 미복용 감지 기록.
 * <p>
 * 배치(MedicationMissDetectionService)가 1분마다 돌면서,
 * "마감 시각이 지났는데 복용 기록이 없는 스케줄"을 발견하면 여기에 1줄 추가한다.
 * <p>
 * <b>역할 분리 — 이 표가 하는 일 / 안 하는 일</b>
 * <ul>
 *     <li>O 미복용 사실을 기록한다 (사실 ledger)</li>
 *     <li>X 알림 보내는 일은 안 한다 (그건 다음 작업 — 연속 누락 감지 + 일일 다이제스트 배치가 이 표를 보고 판단)</li>
 * </ul>
 * <p>
 * <b>제약조건</b>
 * <ul>
 *     <li>UNIQUE (schedule_id, scheduled_date): 같은 스케줄이 같은 날 두 번 기록되지 않도록 DB 레벨에서 막음.
 *         배치가 lookback window 때문에 중복 호출돼도 안전.</li>
 *     <li>INDEX (ward_id, scheduled_date): 보호자 대시보드에서 "특정 어머니의 특정 날짜 미복용 목록" 조회 효율화.</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "medication_miss_record",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_med_miss_schedule_date",
                        columnNames = {"schedule_id", "scheduled_date"}
                )
        },
        indexes = {
                @Index(name = "idx_med_miss_ward_date", columnList = "ward_id, scheduled_date DESC")
        }
)
public class MedicationMissRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    /** 약을 먹었어야 할 날짜 (예: 2026-05-19) */
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    /** 약을 먹었어야 할 시각 (예: 09:00) */
    @Column(name = "scheduled_time", nullable = false)
    private LocalTime scheduledTime;

    /** 마감 시각 = 예정 시각 + 허용 지각 분 (이 시각 지나면 미복용으로 간주) */
    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    /** 배치가 미복용으로 감지한 시각 (디버깅용) */
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Builder
    private MedicationMissRecord(
            Long wardId,
            Long scheduleId,
            LocalDate scheduledDate,
            LocalTime scheduledTime,
            LocalDateTime deadlineAt,
            LocalDateTime detectedAt
    ) {
        this.wardId = wardId;
        this.scheduleId = scheduleId;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.deadlineAt = deadlineAt;
        this.detectedAt = detectedAt;
    }
}