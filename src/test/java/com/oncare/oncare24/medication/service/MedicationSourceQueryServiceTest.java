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
import com.oncare.oncare24.medication.entity.MedicationLogSource;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.security.crypto.service.CommonCryptoService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MedicationSourceQueryServiceTest {

    private static final Long WARD_ID = 1L;
    private static final Long GUARDIAN_ID = 2L;
    private static final byte[] WARD_PRIVATE_KEY = new byte[]{1, 2, 3};

    private EncryptedActivityLogRepository encryptedActivityLogRepository;
    private CommonCryptoService commonCryptoService;
    private MlKemKeyProvisionService mlKemKeyProvisionService;
    private UserRepository userRepository;
    private GuardianWardRepository guardianWardRepository;
    private MedicationSourceQueryService medicationSourceQueryService;

    @BeforeEach
    void setUp() {
        encryptedActivityLogRepository = mock(EncryptedActivityLogRepository.class);
        commonCryptoService = mock(CommonCryptoService.class);
        mlKemKeyProvisionService = mock(MlKemKeyProvisionService.class);
        userRepository = mock(UserRepository.class);
        guardianWardRepository = mock(GuardianWardRepository.class);
        medicationSourceQueryService = new MedicationSourceQueryService(
                encryptedActivityLogRepository,
                commonCryptoService,
                mlKemKeyProvisionService,
                userRepository,
                guardianWardRepository
        );
    }

    @Test
    void findMedicationSchedulesDecryptsLatestScheduleEventByScheduleId() {
        stubElderAccess();
        stubPrivateKey();
        EncryptedActivityLog created = encryptedLog("medication_schedule", 10L, LocalDateTime.of(2026, 5, 11, 8, 0));
        EncryptedActivityLog updated = encryptedLog("medication_schedule", 10L, LocalDateTime.of(2026, 5, 11, 9, 0));
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndSourceTableOrderByOccurredAtAsc(
                WARD_ID,
                ActivityEventType.MEDICATION_EVENT,
                "medication_schedule"
        )).thenReturn(List.of(created, updated));
        stubDecrypt(created, schedulePayload("CREATED", 10L, "old pill", LocalTime.of(9, 0), true));
        stubDecrypt(updated, schedulePayload("UPDATED", 10L, "new pill", LocalTime.of(8, 0), true));

        List<MedicationScheduleSourceResponse> responses =
                medicationSourceQueryService.findMedicationSchedules(WARD_ID, WARD_ID, false);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).scheduleId()).isEqualTo(10L);
        assertThat(responses.get(0).medicationName()).isEqualTo("new pill");
        assertThat(responses.get(0).scheduledTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(responses.get(0).lastChangedAt()).isEqualTo(updated.getOccurredAt());
    }

    @Test
    void findMedicationSchedulesExcludesInactiveByDefaultAndIncludesWhenRequested() {
        stubElderAccess();
        stubPrivateKey();
        EncryptedActivityLog active = encryptedLog("medication_schedule", 10L, LocalDateTime.of(2026, 5, 11, 8, 0));
        EncryptedActivityLog inactive = encryptedLog("medication_schedule", 11L, LocalDateTime.of(2026, 5, 11, 9, 0));
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndSourceTableOrderByOccurredAtAsc(
                WARD_ID,
                ActivityEventType.MEDICATION_EVENT,
                "medication_schedule"
        )).thenReturn(List.of(active, inactive));
        stubDecrypt(active, schedulePayload("CREATED", 10L, "active pill", LocalTime.of(8, 0), true));
        stubDecrypt(inactive, schedulePayload("DEACTIVATED", 11L, "inactive pill", null, false));

        List<MedicationScheduleSourceResponse> activeOnly =
                medicationSourceQueryService.findMedicationSchedules(WARD_ID, WARD_ID, false);
        List<MedicationScheduleSourceResponse> all =
                medicationSourceQueryService.findMedicationSchedules(WARD_ID, WARD_ID, true);

        assertThat(activeOnly).extracting(MedicationScheduleSourceResponse::scheduleId)
                .containsExactly(10L);
        assertThat(all).extracting(MedicationScheduleSourceResponse::scheduleId)
                .containsExactly(10L, 11L);
    }

    @Test
    void findMedicationLogsDecryptsDateLogsAndSortsByScheduledAt() {
        stubElderAccess();
        stubPrivateKey();
        LocalDate date = LocalDate.of(2026, 5, 11);
        EncryptedActivityLog later = encryptedLog("medication_log", 101L, LocalDateTime.of(2026, 5, 11, 12, 10));
        EncryptedActivityLog earlier = encryptedLog("medication_log", 100L, LocalDateTime.of(2026, 5, 11, 8, 10));
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndSourceTableAndOccurredAtBetweenOrderByOccurredAtAsc(
                WARD_ID,
                ActivityEventType.MEDICATION_EVENT,
                "medication_log",
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        )).thenReturn(List.of(later, earlier));
        stubDecrypt(later, logPayload(20L, "lunch pill", LocalDateTime.of(2026, 5, 11, 12, 0), LocalDateTime.of(2026, 5, 11, 12, 10)));
        stubDecrypt(earlier, logPayload(10L, "morning pill", LocalDateTime.of(2026, 5, 11, 8, 0), LocalDateTime.of(2026, 5, 11, 8, 10)));

        List<MedicationLogSourceResponse> responses =
                medicationSourceQueryService.findMedicationLogs(WARD_ID, WARD_ID, date);

        assertThat(responses).extracting(MedicationLogSourceResponse::scheduleId)
                .containsExactly(10L, 20L);
        assertThat(responses.get(0).medicationName()).isEqualTo("morning pill");
        verify(encryptedActivityLogRepository).findByWardIdAndEventTypeAndSourceTableAndOccurredAtBetweenOrderByOccurredAtAsc(
                WARD_ID,
                ActivityEventType.MEDICATION_EVENT,
                "medication_log",
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        );
    }

    @Test
    void acceptedGuardianCanFindMedicationSource() {
        User guardian = user(GUARDIAN_ID, UserRole.GUARDIAN);
        User ward = user(WARD_ID, UserRole.ELDER);
        when(userRepository.findById(GUARDIAN_ID)).thenReturn(Optional.of(guardian));
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
        when(guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                GUARDIAN_ID,
                WARD_ID,
                GuardianWardStatus.ACCEPTED
        )).thenReturn(true);
        stubPrivateKey();
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndSourceTableOrderByOccurredAtAsc(
                WARD_ID,
                ActivityEventType.MEDICATION_EVENT,
                "medication_schedule"
        )).thenReturn(List.of());

        List<MedicationScheduleSourceResponse> responses =
                medicationSourceQueryService.findMedicationSchedules(GUARDIAN_ID, WARD_ID, false);

        assertThat(responses).isEmpty();
    }

    @Test
    void unlinkedGuardianCannotFindMedicationSource() {
        User guardian = user(GUARDIAN_ID, UserRole.GUARDIAN);
        User ward = user(WARD_ID, UserRole.ELDER);
        when(userRepository.findById(GUARDIAN_ID)).thenReturn(Optional.of(guardian));
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
        when(guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                GUARDIAN_ID,
                WARD_ID,
                GuardianWardStatus.ACCEPTED
        )).thenReturn(false);

        assertThatThrownBy(() -> medicationSourceQueryService.findMedicationSchedules(GUARDIAN_ID, WARD_ID, false))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.MEDICATION_ACCESS_DENIED);
        verifyNoInteractions(encryptedActivityLogRepository, commonCryptoService, mlKemKeyProvisionService);
    }

    private void stubElderAccess() {
        User elder = user(WARD_ID, UserRole.ELDER);
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(elder));
    }

    private void stubPrivateKey() {
        when(mlKemKeyProvisionService.readPrivateKey(WARD_ID)).thenReturn(WARD_PRIVATE_KEY);
    }

    private void stubDecrypt(EncryptedActivityLog log, MedicationSchedulePayload payload) {
        when(commonCryptoService.decryptActivityLogPayload(
                eq(log.getDataKeyId()),
                eq(log.getEncryptedPackage()),
                eq(log.getAadJson()),
                eq(WARD_ID),
                eq(CommonCryptoService.OWNER_TYPE_USER),
                any(byte[].class),
                eq(MedicationSchedulePayload.class)
        )).thenReturn(payload);
    }

    private void stubDecrypt(EncryptedActivityLog log, MedicationLogPayload payload) {
        when(commonCryptoService.decryptActivityLogPayload(
                eq(log.getDataKeyId()),
                eq(log.getEncryptedPackage()),
                eq(log.getAadJson()),
                eq(WARD_ID),
                eq(CommonCryptoService.OWNER_TYPE_USER),
                any(byte[].class),
                eq(MedicationLogPayload.class)
        )).thenReturn(payload);
    }

    private MedicationSchedulePayload schedulePayload(
            String action,
            Long scheduleId,
            String medicationName,
            LocalTime scheduledTime,
            boolean active
    ) {
        return new MedicationSchedulePayload(
                action,
                scheduleId,
                medicationName,
                scheduledTime,
                30,
                60,
                MedicationScheduleType.DAILY,
                null,
                active
        );
    }

    private MedicationLogPayload logPayload(
            Long scheduleId,
            String medicationName,
            LocalDateTime scheduledAt,
            LocalDateTime takenAt
    ) {
        return new MedicationLogPayload(
                scheduleId,
                scheduledAt,
                takenAt,
                medicationName,
                MedicationLogSource.USER_INPUT,
                30,
                60
        );
    }

    private EncryptedActivityLog encryptedLog(String sourceTable, Long sourceId, LocalDateTime occurredAt) {
        return EncryptedActivityLog.builder()
                .wardId(WARD_ID)
                .dataKeyId("key-" + sourceTable + "-" + sourceId + "-" + occurredAt)
                .eventType(ActivityEventType.MEDICATION_EVENT)
                .sourceTable(sourceTable)
                .sourceId(sourceId)
                .occurredAt(occurredAt)
                .encryptedPackage(new byte[]{9, sourceId.byteValue()})
                .aadJson("{\"ward_id\":" + WARD_ID + "}")
                .build();
    }

    private User user(Long id, UserRole role) {
        User user = User.builder()
                .phone("010" + id)
                .password("encoded")
                .name(role.name().toLowerCase())
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
