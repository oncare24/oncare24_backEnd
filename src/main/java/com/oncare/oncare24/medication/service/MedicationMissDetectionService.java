package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.medication.entity.MedicationLog;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.entity.MedicationMissRecord;
import com.oncare.oncare24.medication.repository.MedicationLogRepository;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.medication.repository.MedicationMissRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oncare.oncare24.notification.service.NotificationService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 약 미복용 감지 배치.
 * <p>
 * <b>이 클래스가 하는 일 (이 단계 한정)</b>
 * <ol>
 *     <li>1분 주기로 활성 스케줄 전체 스캔</li>
 *     <li>각 스케줄에 대해: 오늘 마감 시각이 직전 2분 안에 지났는가?</li>
 *     <li>지났다면: 오늘 그 스케줄에 대한 복용 기록(MedicationLog)이 있는가?</li>
 *     <li>복용 기록 없으면: medication_miss_record 표에 1줄 INSERT</li>
 * </ol>
 * <p>
 * <b>이 클래스가 하지 않는 일</b>
 * <ul>
 *     <li>X 알림 발송 — 다음 단계 작업(연속 누락 감지, 일일 다이제스트)에서 이 표를 보고 판단</li>
 *     <li>X 복용 시간 도래 알림 — 그건 어머니 폰의 로컬 알림이 따로 처리 (5번 작업)</li>
 * </ul>
 * <p>
 * <b>설계 결정</b>
 * <ul>
 *     <li>1분 주기 + 2분 lookback: EscalationService와 동일 패턴. 배치가 한 번 늦게 돌아도(예: GC, 재기동)
 *         직전 분의 마감 건을 놓치지 않도록 안전망 1분 추가.</li>
 *     <li>UNIQUE 제약 + existsByScheduleIdAndScheduledDate 사전 체크 조합: 2분 lookback 때문에
 *         같은 건이 2번 감지될 수 있으나, DB 제약과 사전 체크가 이중 안전망.</li>
 *     <li>WEEKLY 스케줄: 오늘 요일(DayOfWeek)이 스케줄의 dayOfWeek와 같을 때만 검사.
 *         DAILY는 무조건 매일 검사.</li>
 *     <li>자정 경계: 23:59 마감 + lookback 2분 → 다음날 00:01에 배치가 돌면 어제 날짜로 기록.
 *         deadline_at의 LocalDate를 그대로 scheduledDate로 씀.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationMissDetectionService {

    /** 배치 주기: 1분 */
    private static final long BATCH_INTERVAL_MS = 60 * 1000L;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    /** 직전 몇 분치를 검사할지 (배치가 한 번 늦게 돌아도 놓치지 않도록 1분치 여유) */
    private static final long LOOKBACK_MINUTES = 2;

    private final MedicationScheduleRepository scheduleRepository;
    private final MedicationLogRepository logRepository;
    private final MedicationMissRecordRepository missRecordRepository;

    @Scheduled(fixedDelay = BATCH_INTERVAL_MS, initialDelay = BATCH_INTERVAL_MS)
    @Transactional
    public void detectMissedMedications() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lookbackStart = now.minusMinutes(LOOKBACK_MINUTES);

        List<MedicationSchedule> activeSchedules = scheduleRepository.findByActiveTrueOrderByWardIdAscScheduledTimeAsc();

        if (activeSchedules.isEmpty()) {
            return;
        }

        int detected = 0;
        for (MedicationSchedule schedule : activeSchedules) {
            if (!isApplicableToday(schedule, now)) {
                continue;
            }

            LocalDateTime deadlineAt = computeDeadlineAt(schedule, now.toLocalDate());

            // 마감 시각이 이번 배치 검사 구간 안에 들어왔는가?
            // (lookbackStart, now] — 직전 2분 안에 마감이 지난 건만 대상
            if (deadlineAt.isBefore(lookbackStart) || !deadlineAt.isBefore(now)) {
                continue;
            }

            LocalDate scheduledDate = deadlineAt.toLocalDate();

            // 이미 이번 날짜에 미복용으로 기록된 건은 건너뛴다 (2분 lookback 중복 방지)
            if (missRecordRepository.existsByScheduleIdAndScheduledDate(schedule.getId(), scheduledDate)) {
                continue;
            }

            // 오늘 이 스케줄에 대한 복용 기록이 있는가?
            // 윈도우: scheduled_time - allowedEarly ~ scheduled_time + allowedDelay
            // (이 범위 안의 복용은 정상 복용으로 인정)
            LocalDateTime windowStart = LocalDateTime.of(scheduledDate, schedule.getScheduledTime())
                    .minusMinutes(schedule.getAllowedEarlyMinutes());
            LocalDateTime windowEnd = deadlineAt;
            Optional<MedicationLog> existingLog = logRepository.findFirstByScheduleIdAndTakenAtBetweenOrderByTakenAtAsc(
                    schedule.getId(), windowStart, windowEnd
            );

            if (existingLog.isPresent()) {
                continue; // 정상 복용함 — 미복용 아님
            }

            // 미복용 확정 — 표에 기록
            missRecordRepository.save(MedicationMissRecord.builder()
                    .wardId(schedule.getWardId())
                    .scheduleId(schedule.getId())
                    .scheduledDate(scheduledDate)
                    .scheduledTime(schedule.getScheduledTime())
                    .deadlineAt(deadlineAt)
                    .detectedAt(now)
                    .build());
            detected++;

            log.info("[MED-MISS] detected wardId={}, scheduleId={}, medicationName={}, scheduledAt={}",
                    schedule.getWardId(), schedule.getId(), schedule.getMedicationName(),
                    LocalDateTime.of(scheduledDate, schedule.getScheduledTime()));
            if (isConsecutiveMiss(schedule, scheduledDate)) {
                notifyGuardiansOfConsecutiveMiss(schedule, scheduledDate);
            }
        }

        if (detected > 0) {
            log.info("[MED-MISS-BATCH] {} missed medication(s) recorded", detected);
        }
    }

    /**
     * 이 스케줄이 오늘 적용되는지 판단.
     * DAILY: 매일 적용
     * WEEKLY: 오늘 요일이 스케줄의 dayOfWeek와 같을 때만 적용
     */
    private boolean isApplicableToday(MedicationSchedule schedule, LocalDateTime now) {
        MedicationScheduleType type = schedule.getScheduleType();
        if (type == MedicationScheduleType.DAILY) {
            return true;
        }
        if (type == MedicationScheduleType.WEEKLY) {
            DayOfWeek today = now.getDayOfWeek();
            return today.equals(schedule.getDayOfWeek());
        }
        return false;
    }

    /**
     * 마감 시각 계산: 오늘 날짜 + 예정 시각 + 허용 지각 분.
     * 예: 오늘 2026-05-19, scheduledTime=09:00, allowedDelay=30 → 2026-05-19T09:30
     */
    private LocalDateTime computeDeadlineAt(MedicationSchedule schedule, LocalDate today) {
        LocalTime scheduledTime = schedule.getScheduledTime();
        return LocalDateTime.of(today, scheduledTime).plusMinutes(schedule.getAllowedDelayMinutes());
    }

    /**
     * 직전 예정일에도 같은 스케줄이 미복용으로 기록되어 있는가?
     * <p>
     * DAILY: 어제 — scheduledDate.minusDays(1)
     * WEEKLY: 7일 전 — scheduledDate.minusDays(7)
     * <p>
     * <b>주의</b>: "어제 약이 추가됐고 빼먹음 → 오늘 또 빼먹음" 같은 케이스는 연속 2로 잡힘.
     * 신규 스케줄도 동일 룰 적용 (의도된 동작).
     */
    private boolean isConsecutiveMiss(MedicationSchedule schedule, LocalDate scheduledDate) {
        LocalDate previousExpected = switch (schedule.getScheduleType()) {
            case DAILY -> scheduledDate.minusDays(1);
            case WEEKLY -> scheduledDate.minusDays(7);
        };
        return missRecordRepository.existsByScheduleIdAndScheduledDate(
                schedule.getId(), previousExpected);
    }

    /**
     * 보호자 전원에게 연속 누락 알림 발송 위임.
     * <p>
     * 발송 자체(설정 필터링, FCM 호출, 히스토리 기록)는 NotificationService가 담당.
     * 여기서는 알림에 필요한 정보(ward 이름) 채워서 호출만.
     */
    private void notifyGuardiansOfConsecutiveMiss(MedicationSchedule schedule, LocalDate scheduledDate) {
        User ward = userRepository.findById(schedule.getWardId()).orElse(null);
        if (ward == null) {
            log.warn("[MED-MISS-NOTIFY-SKIP] ward {} not found", schedule.getWardId());
            return;
        }

        String medicationName = schedule.getMedicationName() != null
                ? schedule.getMedicationName()
                : "약";

        notificationService.notifyMedicationMissed(
                schedule.getWardId(),
                ward.getName(),
                schedule.getId(),
                medicationName,
                2  // 현재 단계는 항상 "2일 연속". 추후 3일/4일 연속까지 셀 거면 별도 카운팅.
        );
    }
}