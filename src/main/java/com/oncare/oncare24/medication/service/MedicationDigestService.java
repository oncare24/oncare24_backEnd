package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.entity.MedicationMissRecord;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.repository.MedicationMissRecordRepository;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.notification.entity.GuardianNotificationPreference;
import com.oncare.oncare24.notification.repository.GuardianNotificationPreferenceRepository;
import com.oncare.oncare24.notification.service.NotificationService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 매일 저녁 미복용 요약(다이제스트) 발송 배치.
 * <p>
 * <b>이 클래스가 하는 일</b>
 * <ol>
 *     <li>1분 주기로 GuardianNotificationPreference 스캔</li>
 *     <li>"다이제스트 ON + 요약 시각 이미 지남 + 오늘 미발송" 보호자만 추출</li>
 *     <li>해당 보호자의 ACCEPTED 어머니들 조회</li>
 *     <li>어머니별 오늘 미복용 기록 조회 → 어머니당 알림 1개 발송 (NotificationService 위임)</li>
 *     <li>보호자의 last_digest_sent_at 마킹</li>
 * </ol>
 * <p>
 * <b>설계 결정</b>
 * <ul>
 *     <li>어머니별 알림 분리: 보호자 앱이 알림 탭 시 어느 어머니의 복약 화면으로 갈지 명확.
 *         하나로 합치면 라우팅 모호해짐. (Medisafe 케어기버, Life360 패턴 동일)</li>
 *     <li>미복용 0건인 어머니는 발송 안 함. 보호자 입장에서 "0건"이라는 알림 자체가 noise.</li>
 *     <li>last_digest_sent_at 마킹은 어머니별 미복용 유무와 무관하게 보호자 단위로 1회.
 *         같은 보호자가 매분 재처리되는 것 방지.</li>
 *     <li>알림 설정 행이 없는 보호자는 이번 배치에서 제외. step 4 회원가입 흐름에서 default 행 자동 생성 예정.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationDigestService {

    /** 배치 주기: 1분 */
    private static final long BATCH_INTERVAL_MS = 60 * 1000L;

    private final GuardianNotificationPreferenceRepository preferenceRepository;
    private final GuardianWardRepository guardianWardRepository;
    private final MedicationMissRecordRepository missRecordRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = BATCH_INTERVAL_MS, initialDelay = BATCH_INTERVAL_MS)
    @Transactional
    public void sendDailyDigests() {
        LocalDateTime now = LocalDateTime.now();
        LocalTime nowTime = LocalTime.of(now.getHour(), now.getMinute());
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();

        List<GuardianNotificationPreference> pending =
                preferenceRepository.findPendingDigestRecipients(nowTime, todayStart);

        if (pending.isEmpty()) {
            return;
        }

        for (GuardianNotificationPreference pref : pending) {
            try {
                sendDigestForGuardian(pref, now);
            } catch (Exception e) {
                // 한 보호자 실패가 다른 보호자 발송 막지 않도록 격리
                log.error("[MED-DIGEST-ERR] guardianId={} failed: {}",
                        pref.getGuardianId(), e.getMessage(), e);
            }
        }
    }

    private void sendDigestForGuardian(GuardianNotificationPreference pref, LocalDateTime now) {
        Long guardianId = pref.getGuardianId();
        LocalDate today = now.toLocalDate();

        List<GuardianWard> links = guardianWardRepository
                .findByGuardianIdAndStatusOrderByCreatedAtDesc(guardianId, GuardianWardStatus.ACCEPTED);

        if (links.isEmpty()) {
            pref.markDigestSent(now);
            return;
        }

        int wardsWithMisses = 0;
        for (GuardianWard link : links) {
            Long wardId = link.getWardId();
            List<MedicationMissRecord> misses = missRecordRepository
                    .findByWardIdAndScheduledDateOrderByScheduledTimeAsc(wardId, today);

            if (misses.isEmpty()) {
                continue; // 이 어머니는 오늘 미복용 없음 — 발송 안 함
            }

            User ward = userRepository.findById(wardId).orElse(null);
            if (ward == null) continue;

            String medicationSummary = buildMedicationSummary(misses);
            notificationService.notifyMedicationDigest(
                    guardianId,
                    wardId,
                    ward.getName(),
                    misses.size(),
                    medicationSummary
            );
            wardsWithMisses++;
        }

        pref.markDigestSent(now); // 발송 여부 무관 — 오늘 처리 완료 표시

        log.info("[MED-DIGEST] guardianId={}, wardCount={}, wardsWithMisses={}",
                guardianId, links.size(), wardsWithMisses);
    }

    /**
     * 미복용 기록 N건 → 본문에 들어갈 약 이름 요약 문자열.
     * <p>
     * 같은 약을 아침/저녁 2번 빼먹어도 약 이름은 중복 제거.
     * <ul>
     *     <li>1개: "혈압약"</li>
     *     <li>2개: "혈압약, 당뇨약"</li>
     *     <li>3개 이상: "혈압약 외 2건"</li>
     * </ul>
     */
    private String buildMedicationSummary(List<MedicationMissRecord> misses) {
        List<Long> scheduleIds = misses.stream()
                .map(MedicationMissRecord::getScheduleId)
                .distinct()
                .toList();
        List<MedicationSchedule> schedules = scheduleRepository.findAllById(scheduleIds);

        Map<Long, String> nameById = new HashMap<>();
        for (MedicationSchedule s : schedules) {
            String name = s.getMedicationName() != null ? s.getMedicationName() : "약";
            nameById.put(s.getId(), name);
        }

        List<String> uniqueNames = misses.stream()
                .map(m -> nameById.getOrDefault(m.getScheduleId(), "약"))
                .distinct()
                .toList();

        if (uniqueNames.size() == 1) {
            return uniqueNames.get(0);
        }
        if (uniqueNames.size() == 2) {
            return uniqueNames.get(0) + ", " + uniqueNames.get(1);
        }
        return uniqueNames.get(0) + " 외 " + (uniqueNames.size() - 1) + "건";
    }
}