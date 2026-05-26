package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.AnalysisType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.analysis.service.AnalysisStateService;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.medication.dto.MedicationLogPayload;
import com.oncare.oncare24.medication.dto.MedicationAnalysisResult;
import com.oncare.oncare24.medication.dto.MedicationSchedulePayload;
import com.oncare.oncare24.medication.entity.MedicationAnalysisStatus;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.security.crypto.service.CommonCryptoService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicationAnalysisService {

    private final MedicationScheduleRepository medicationScheduleRepository;
    private final UserRepository userRepository;
    private final EncryptedActivityLogRepository encryptedActivityLogRepository;
    private final CommonCryptoService commonCryptoService;
    private final MlKemKeyProvisionService mlKemKeyProvisionService;
    private final AnalysisStateService analysisStateService;

    @Transactional(readOnly = true)
    // 단일 ward 복약 분석 진입점
    public List<MedicationAnalysisResult> analyzeWardMedication(Long wardId, LocalDate analysisDate) {
        assertWardIsElder(wardId);

        List<MedicationSchedule> schedules = medicationScheduleRepository
                .findByWardIdAndActiveTrueOrderByScheduledTimeAsc(wardId);

        return analyzeSchedules(wardId, schedules, analysisDate, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    // 전체 활성 ward 복약 분석 진입점
    public List<MedicationAnalysisResult> analyzeAllActiveWardMedication(LocalDate analysisDate) {
        List<MedicationSchedule> schedules = medicationScheduleRepository
                .findByActiveTrueOrderByWardIdAscScheduledTimeAsc();

        LocalDateTime now = LocalDateTime.now();
        return schedules.stream()
                .collect(Collectors.groupingBy(MedicationSchedule::getWardId))
                .entrySet()
                .stream()
                .flatMap(entry -> analyzeSchedules(entry.getKey(), entry.getValue(), analysisDate, now).stream())
                .sorted(Comparator
                        .comparing(MedicationAnalysisResult::wardId)
                        .thenComparing(MedicationAnalysisResult::scheduledAt)
                        .thenComparing(MedicationAnalysisResult::scheduleId))
                .toList();
    }

    // 암호화 복약 원천 복호화 후 분석
    private List<MedicationAnalysisResult> analyzeSchedules(
            Long wardId,
            List<MedicationSchedule> schedules,
            LocalDate analysisDate,
            LocalDateTime now
    ) {
        // 분석 대상 ward 개인키로 암호화된 복약 원천 데이터 복호화 준비
        byte[] wardPrivateKey = readWardPrivateKey(wardId, schedules);
        List<DecryptedSchedule> decryptedSchedules = schedules.stream()
                // 최신 복약 일정 이벤트를 복호화해서 분석 입력으로 사용
                .map(schedule -> decryptLatestSchedule(schedule, wardPrivateKey))
                .flatMap(Optional::stream)
                .filter(schedule -> schedule.payload().active())
                .filter(schedule -> isTargetSchedule(schedule.payload(), analysisDate))
                .toList();

        if (decryptedSchedules.isEmpty()) {
            return List.of();
        }

        LocalDateTime searchStart = decryptedSchedules.stream()
                .map(schedule -> windowStartAt(schedule.payload(), analysisDate))
                .min(LocalDateTime::compareTo)
                .orElse(analysisDate.atStartOfDay());
        LocalDateTime searchEnd = decryptedSchedules.stream()
                .map(schedule -> nextScheduledAt(schedule.payload(), analysisDate))
                .max(LocalDateTime::compareTo)
                .orElse(analysisDate.plusDays(2).atStartOfDay());
        List<Long> scheduleIds = decryptedSchedules.stream()
                .map(DecryptedSchedule::scheduleId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<MedicationLogPayload>> logsByScheduleId = encryptedActivityLogRepository
                .findByWardIdAndEventTypeAndSourceTableAndOccurredAtBetweenOrderByOccurredAtAsc(
                        resolveWardId(wardId, schedules),
                        ActivityEventType.MEDICATION_EVENT,
                        "medication_log",
                        searchStart,
                        searchEnd
                )
                .stream()
                // 분석 구간의 복약 기록 이벤트를 복호화해서 일정별로 묶음
                .map(log -> decryptActivityPayload(log, wardPrivateKey, MedicationLogPayload.class))
                .filter(log -> log.scheduleId() != null)
                .filter(log -> scheduleIds.contains(log.scheduleId()))
                .collect(Collectors.groupingBy(MedicationLogPayload::scheduleId));

        List<MedicationAnalysisResult> results = decryptedSchedules.stream()
                .map(schedule -> analyzeSchedule(schedule, analysisDate, now, logsByScheduleId))
                .sorted(Comparator
                        .comparing(MedicationAnalysisResult::wardId)
                        .thenComparing(MedicationAnalysisResult::scheduledAt)
                        .thenComparing(MedicationAnalysisResult::scheduleId))
                .toList();
        results.forEach(result -> persistMedicationState(result, now));
        return results;
    }

    private MedicationAnalysisResult analyzeSchedule(
            DecryptedSchedule schedule,
            LocalDate analysisDate,
            LocalDateTime now,
            Map<Long, List<MedicationLogPayload>> logsByScheduleId
    ) {
        MedicationSchedulePayload payload = schedule.payload();
        LocalDateTime scheduledAt = LocalDateTime.of(analysisDate, payload.scheduledTime());
        LocalDateTime windowStartAt = windowStartAt(payload, analysisDate);
        LocalDateTime deadlineAt = scheduledAt.plusMinutes(payload.allowedDelayMinutes());
        Optional<MedicationLogPayload> log = findMedicationLogForSchedule(
                schedule.scheduleId(),
                windowStartAt,
                nextScheduledAt(payload, analysisDate),
                logsByScheduleId
        );

        MedicationAnalysisStatus status = determineStatus(log.orElse(null), windowStartAt, deadlineAt, now);

        return new MedicationAnalysisResult(
                schedule.wardId(),
                schedule.scheduleId(),
                payload.medicationName(),
                windowStartAt,
                scheduledAt,
                deadlineAt,
                log.map(MedicationLogPayload::takenAt).orElse(null),
                status,
                payload.allowedEarlyMinutes(),
                payload.allowedDelayMinutes(),
                buildDetailMessage(status, payload.medicationName())
        );
    }

    private Optional<MedicationLogPayload> findMedicationLogForSchedule(
            Long scheduleId,
            LocalDateTime windowStartAt,
            LocalDateTime nextScheduledAt,
            Map<Long, List<MedicationLogPayload>> logsByScheduleId
    ) {
        return logsByScheduleId.getOrDefault(scheduleId, List.of())
                .stream()
                .filter(log -> log.takenAt() != null)
                .filter(log -> !log.takenAt().isBefore(windowStartAt))
                .filter(log -> log.takenAt().isBefore(nextScheduledAt))
                .findFirst();
    }

    private MedicationAnalysisStatus determineStatus(
            MedicationLogPayload log,
            LocalDateTime windowStartAt,
            LocalDateTime deadlineAt,
            LocalDateTime now
    ) {
        if (log != null) {
            if (!log.takenAt().isBefore(windowStartAt) && !log.takenAt().isAfter(deadlineAt)) {
                return MedicationAnalysisStatus.ON_TIME;
            }
            return MedicationAnalysisStatus.DELAYED;
        }

        if (now.isAfter(deadlineAt)) {
            return MedicationAnalysisStatus.MISSED;
        }

        return MedicationAnalysisStatus.PENDING;
    }

    private void persistMedicationState(MedicationAnalysisResult result, LocalDateTime analyzedAt) {
        Integer statusCode = medicationStatusCode(result.status());
        if (statusCode == null) {
            return;
        }
        analysisStateService.upsertLatestState(
                result.wardId(),
                AnalysisType.MEDICATION,
                statusCode,
                analyzedAt
        );
    }

    private Integer medicationStatusCode(MedicationAnalysisStatus status) {
        return switch (status) {
            case ON_TIME -> 0;
            case DELAYED -> 1;
            case MISSED -> 2;
            case PENDING -> null;
        };
    }

    private boolean isTargetSchedule(MedicationSchedulePayload schedule, LocalDate analysisDate) {
        if (schedule.scheduleType() == MedicationScheduleType.DAILY) {
            return true;
        }

        return schedule.scheduleType() == MedicationScheduleType.WEEKLY
                && schedule.dayOfWeek() == analysisDate.getDayOfWeek();
    }

    private LocalDateTime nextScheduledAt(MedicationSchedulePayload schedule, LocalDate analysisDate) {
        int daysToAdd = schedule.scheduleType() == MedicationScheduleType.WEEKLY ? 7 : 1;
        return LocalDateTime.of(analysisDate.plusDays(daysToAdd), schedule.scheduledTime());
    }

    private LocalDateTime windowStartAt(MedicationSchedulePayload schedule, LocalDate analysisDate) {
        return LocalDateTime.of(analysisDate, schedule.scheduledTime())
                .minusMinutes(schedule.allowedEarlyMinutes());
    }

    private String buildDetailMessage(MedicationAnalysisStatus status, String medicationName) {
        return switch (status) {
            case ON_TIME -> medicationName + " was recorded within the allowed medication window.";
            case DELAYED -> medicationName + " was recorded after the allowed medication deadline.";
            case MISSED -> medicationName + " has no medication log after the allowed deadline.";
            case PENDING -> medicationName + " is waiting for a medication log.";
        };
    }

    private void assertWardIsElder(Long wardId) {
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (ward.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.INVALID_ELDER);
        }
    }

    // 분석용 ward 개인키 조회
    private byte[] readWardPrivateKey(Long wardId, List<MedicationSchedule> schedules) {
        Long resolvedWardId = resolveWardId(wardId, schedules);
        return mlKemKeyProvisionService.readPrivateKey(resolvedWardId);
    }

    private Long resolveWardId(Long wardId, List<MedicationSchedule> schedules) {
        if (wardId != null) {
            return wardId;
        }
        return schedules.stream()
                .map(MedicationSchedule::getWardId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("wardId cannot be resolved from medication schedules"));
    }

    // 최신 복약 일정 암호화 이벤트 복호화
    private Optional<DecryptedSchedule> decryptLatestSchedule(MedicationSchedule schedule, byte[] wardPrivateKey) {
        // 복약 일정의 최신 encrypted_activity_log를 조회해 복호화
        return encryptedActivityLogRepository
                .findFirstBySourceTableAndSourceIdAndEventTypeOrderByOccurredAtDesc(
                        "medication_schedule",
                        schedule.getId(),
                        ActivityEventType.MEDICATION_EVENT
                )
                .map(log -> decryptActivityPayload(log, wardPrivateKey, MedicationSchedulePayload.class))
                .map(payload -> new DecryptedSchedule(schedule.getWardId(), schedule.getId(), payload));
    }

    // 복약 분석 원천 payload 복호화
    private <T> T decryptActivityPayload(EncryptedActivityLog log, byte[] wardPrivateKey, Class<T> payloadType) {
        // 암호화된 복약 분석 원천 payload 복호화
        return commonCryptoService.decryptActivityLogPayload(
                log.getDataKeyId(),
                log.getEncryptedPackage(),
                log.getAadJson(),
                log.getWardId(),
                CommonCryptoService.OWNER_TYPE_USER,
                wardPrivateKey,
                payloadType
        );
    }

    private record DecryptedSchedule(Long wardId, Long scheduleId, MedicationSchedulePayload payload) {
    }
}
