package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.dto.MedicationLogPayload;
import com.oncare.oncare24.medication.dto.MedicationLogSourceResponse;
import com.oncare.oncare24.medication.dto.MedicationSchedulePayload;
import com.oncare.oncare24.medication.dto.MedicationScheduleSourceResponse;
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
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MedicationSourceQueryService {

    private static final String SOURCE_TABLE_SCHEDULE = "medication_schedule";
    private static final String SOURCE_TABLE_LOG = "medication_log";

    private final EncryptedActivityLogRepository encryptedActivityLogRepository;
    private final CommonCryptoService commonCryptoService;
    private final MlKemKeyProvisionService mlKemKeyProvisionService;
    private final UserRepository userRepository;
    private final GuardianWardRepository guardianWardRepository;

    @Transactional(readOnly = true)
    // 암호화된 복약 일정 원천 조회
    public List<MedicationScheduleSourceResponse> findMedicationSchedules(
            Long currentUserId,
            Long wardId,
            boolean includeInactive
    ) {
        assertCanAccessWard(currentUserId, wardId);

        // ward 개인키로 복약 일정 암호화 원천 데이터 복호화 준비
        byte[] wardPrivateKey = mlKemKeyProvisionService.readPrivateKey(wardId);
        Map<Long, ScheduleEvent> latestByScheduleId = new LinkedHashMap<>();
        encryptedActivityLogRepository
                .findByWardIdAndEventTypeAndSourceTableOrderByIdAsc(
                        wardId,
                        ActivityEventType.MEDICATION_EVENT,
                        SOURCE_TABLE_SCHEDULE
                )
                .stream()
                // encrypted_activity_log의 복약 일정 이벤트를 복호화
                .map(log -> decryptScheduleEvent(log, wardPrivateKey))
                .filter(event -> event.payload().scheduleId() != null)
                .forEach(event -> latestByScheduleId.put(event.payload().scheduleId(), event));
        return latestByScheduleId.values()
                .stream()
                .filter(event -> includeInactive || isActiveSchedule(event.payload()))
                .map(this::toScheduleResponse)
                .sorted(Comparator
                        .comparing(
                                MedicationScheduleSourceResponse::scheduledTime,
                                Comparator.nullsLast(LocalTime::compareTo)
                        )
                        .thenComparing(
                                MedicationScheduleSourceResponse::scheduleId,
                                Comparator.nullsLast(Long::compareTo)
                        ))
                .toList();
    }

    @Transactional(readOnly = true)
    // 암호화된 복약 기록 원천 조회
    public List<MedicationLogSourceResponse> findMedicationLogs(
            Long currentUserId,
            Long wardId,
            LocalDate date
    ) {
        assertCanAccessWard(currentUserId, wardId);

        // ward 개인키로 복약 기록 암호화 원천 데이터 복호화 준비
        byte[] wardPrivateKey = mlKemKeyProvisionService.readPrivateKey(wardId);
        List<EncryptedActivityLog> logs = date == null
                ? encryptedActivityLogRepository.findByWardIdAndEventTypeAndSourceTableOrderByOccurredAtAsc(
                wardId,
                ActivityEventType.MEDICATION_EVENT,
                SOURCE_TABLE_LOG
        )
                : encryptedActivityLogRepository.findByWardIdAndEventTypeAndSourceTableAndOccurredAtBetweenOrderByOccurredAtAsc(
                wardId,
                ActivityEventType.MEDICATION_EVENT,
                SOURCE_TABLE_LOG,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        );

        return logs.stream()
                // encrypted_activity_log의 복약 기록 이벤트를 복호화
                .map(log -> decryptActivityPayload(log, wardPrivateKey, MedicationLogPayload.class))
                .map(this::toLogResponse)
                .sorted(Comparator
                        .comparing(
                                this::medicationLogSortAt,
                                Comparator.nullsLast(LocalDateTime::compareTo)
                        )
                        .thenComparing(
                                MedicationLogSourceResponse::takenAt,
                                Comparator.nullsLast(LocalDateTime::compareTo)
                        ))
                .toList();
    }

    private LocalDateTime medicationLogSortAt(MedicationLogSourceResponse response) {
        return response.scheduledAt() != null ? response.scheduledAt() : response.takenAt();
    }

    // 복약 일정 암호화 이벤트 복호화
    private ScheduleEvent decryptScheduleEvent(EncryptedActivityLog log, byte[] wardPrivateKey) {
        // 암호화된 복약 일정 payload 복호화
        MedicationSchedulePayload payload = decryptActivityPayload(log, wardPrivateKey, MedicationSchedulePayload.class);
        return new ScheduleEvent(payload, log.getOccurredAt());
    }

    // 복약 원천 payload 공통 복호화
    private <T> T decryptActivityPayload(EncryptedActivityLog log, byte[] wardPrivateKey, Class<T> payloadType) {
        // encrypted_activity_log payload를 CommonCryptoService로 복호화
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

    private boolean isActiveSchedule(MedicationSchedulePayload payload) {
        return payload.active() && !isDeactivateAction(payload.action());
    }

    private boolean isDeactivateAction(String action) {
        return action != null
                && ("DEACTIVATE".equalsIgnoreCase(action) || "DEACTIVATED".equalsIgnoreCase(action));
    }

    // 복호화된 복약 일정 응답 변환
    private MedicationScheduleSourceResponse toScheduleResponse(ScheduleEvent event) {
        MedicationSchedulePayload payload = event.payload();
        return new MedicationScheduleSourceResponse(
                payload.scheduleId(),
                payload.medicationName(),
                payload.scheduledTime(),
                payload.allowedEarlyMinutes(),
                payload.allowedDelayMinutes(),
                payload.scheduleType(),
                payload.dayOfWeek(),
                normalizeDays(payload),
                payload.active(),
                event.occurredAt(),
                payload.startDate(),   // 추가
                payload.endDate()      // 추가
        );
    }

    private List<java.time.DayOfWeek> normalizeDays(MedicationSchedulePayload payload) {
        if (payload.daysOfWeek() != null && !payload.daysOfWeek().isEmpty()) {
            return payload.daysOfWeek();
        }
        return payload.dayOfWeek() == null ? List.of() : List.of(payload.dayOfWeek());
    }

    // 복호화된 복약 기록 응답 변환
    private MedicationLogSourceResponse toLogResponse(MedicationLogPayload payload) {
        return new MedicationLogSourceResponse(
                payload.scheduleId(),
                payload.medicationName(),
                payload.plannedAt(),
                payload.takenAt(),
                payload.logSource(),
                payload.allowedEarlyMinutes(),
                payload.allowedDelayMinutes()
        );
    }

    private void assertCanAccessWard(Long currentUserId, Long wardId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ELDER));

        if (ward.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.INVALID_ELDER);
        }

        if (currentUser.getRole() == UserRole.ELDER && currentUserId.equals(wardId)) {
            return;
        }

        if (currentUser.getRole() == UserRole.GUARDIAN
                && guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                currentUserId,
                wardId,
                GuardianWardStatus.ACCEPTED
        )) {
            return;
        }

        throw new CustomException(ErrorCode.MEDICATION_ACCESS_DENIED);
    }

    private record ScheduleEvent(MedicationSchedulePayload payload, LocalDateTime occurredAt) {
    }
}
