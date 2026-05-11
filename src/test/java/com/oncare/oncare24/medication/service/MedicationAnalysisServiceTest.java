package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.analysis.entity.AnalysisType;
import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.analysis.service.AnalysisStateService;
import com.oncare.oncare24.medication.dto.MedicationLogPayload;
import com.oncare.oncare24.medication.dto.MedicationAnalysisResult;
import com.oncare.oncare24.medication.dto.MedicationSchedulePayload;
import com.oncare.oncare24.medication.entity.MedicationAnalysisStatus;
import com.oncare.oncare24.medication.entity.MedicationLogSource;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.security.crypto.service.CommonCryptoService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationAnalysisServiceTest {

    private static final Long WARD_ID = 1L;
    private static final Long SCHEDULE_ID = 10L;

    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EncryptedActivityLogRepository encryptedActivityLogRepository;

    @Mock
    private CommonCryptoService commonCryptoService;

    @Mock
    private MlKemKeyProvisionService mlKemKeyProvisionService;

    @Mock
    private AnalysisStateService analysisStateService;

    private MedicationAnalysisService medicationAnalysisService;

    @BeforeEach
    void setUp() {
        medicationAnalysisService = new MedicationAnalysisService(
                medicationScheduleRepository,
                userRepository,
                encryptedActivityLogRepository,
                commonCryptoService,
                mlKemKeyProvisionService,
                analysisStateService
        );

        User ward = User.builder()
                .phone("01012345678")
                .password("encoded-password")
                .name("ward")
                .role(UserRole.ELDER)
                .build();

        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
    }

    @Test
    void analyzeWardMedication_returnsOnTimeWhenTakenWithinEarlyWindow() {
        LocalDate analysisDate = LocalDate.now();
        MedicationAnalysisResult result = analyzeWithLog(
                analysisDate,
                LocalDateTime.of(analysisDate, LocalTime.of(7, 55))
        );

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.ON_TIME);
        assertThat(result.windowStartAt()).isEqualTo(LocalDateTime.of(analysisDate, LocalTime.of(7, 50)));
        assertThat(result.allowedEarlyMinutes()).isEqualTo(10);
        verify(analysisStateService).upsertLatestState(
                eq(WARD_ID),
                eq(AnalysisType.MEDICATION),
                eq(0),
                any(LocalDateTime.class)
        );
    }

    @Test
    void analyzeWardMedication_doesNotTreatTooEarlyLogAsOnTime() {
        LocalDate analysisDate = LocalDate.now().minusDays(1);
        MedicationAnalysisResult result = analyzeWithLogs(
                analysisDate,
                List.of(logPayload(LocalDateTime.of(analysisDate, LocalTime.of(7, 40))))
        );

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.MISSED);
        assertThat(result.takenAt()).isNull();
        verify(analysisStateService).upsertLatestState(
                eq(WARD_ID),
                eq(AnalysisType.MEDICATION),
                eq(2),
                any(LocalDateTime.class)
        );
    }

    @Test
    void analyzeWardMedication_returnsOnTimeWhenTakenBeforeDeadline() {
        LocalDate analysisDate = LocalDate.now();
        MedicationAnalysisResult result = analyzeWithLog(
                analysisDate,
                LocalDateTime.of(analysisDate, LocalTime.of(8, 20))
        );

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.ON_TIME);
    }

    @Test
    void analyzeWardMedication_returnsDelayedWhenTakenAfterDeadline() {
        LocalDate analysisDate = LocalDate.now();
        MedicationAnalysisResult result = analyzeWithLog(
                analysisDate,
                LocalDateTime.of(analysisDate, LocalTime.of(8, 40))
        );

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.DELAYED);
        verify(analysisStateService).upsertLatestState(
                eq(WARD_ID),
                eq(AnalysisType.MEDICATION),
                eq(1),
                any(LocalDateTime.class)
        );
    }

    @Test
    void analyzeWardMedication_returnsMissedWhenDeadlinePassedWithoutValidLog() {
        LocalDate analysisDate = LocalDate.now().minusDays(1);
        MedicationAnalysisResult result = analyzeWithLogs(analysisDate, List.of());

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.MISSED);
        verify(analysisStateService).upsertLatestState(
                eq(WARD_ID),
                eq(AnalysisType.MEDICATION),
                eq(2),
                any(LocalDateTime.class)
        );
    }

    @Test
    void analyzeWardMedication_returnsPendingWhenDeadlineHasNotPassedWithoutValidLog() {
        LocalDate analysisDate = LocalDate.now().plusDays(1);
        MedicationAnalysisResult result = analyzeWithLogs(analysisDate, List.of());

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.PENDING);
        verify(analysisStateService, never()).upsertLatestState(
                eq(WARD_ID),
                eq(AnalysisType.MEDICATION),
                any(Integer.class),
                any(LocalDateTime.class)
        );
    }


    private MedicationAnalysisResult analyzeWithLog(LocalDate analysisDate, LocalDateTime takenAt) {
        return analyzeWithLogs(analysisDate, List.of(logPayload(takenAt)));
    }

    private MedicationAnalysisResult analyzeWithLogs(LocalDate analysisDate, List<MedicationLogPayload> logs) {
        MedicationSchedule schedule = schedule();
        EncryptedActivityLog scheduleLog = encryptedLog("medication_schedule", SCHEDULE_ID, analysisDate.atTime(8, 0));
        List<EncryptedActivityLog> logEvents = logs.stream()
                .map(log -> encryptedLog("medication_log", 100L + logs.indexOf(log), log.takenAt()))
                .toList();

        when(medicationScheduleRepository.findByWardIdAndActiveTrueOrderByScheduledTimeAsc(WARD_ID))
                .thenReturn(List.of(schedule));
        when(mlKemKeyProvisionService.readPrivateKey(WARD_ID)).thenReturn(new byte[] {1});
        when(encryptedActivityLogRepository.findFirstBySourceTableAndSourceIdAndEventTypeOrderByOccurredAtDesc(
                "medication_schedule",
                SCHEDULE_ID,
                ActivityEventType.MEDICATION_EVENT
        )).thenReturn(Optional.of(scheduleLog));
        when(commonCryptoService.decryptActivityLogPayload(
                eq(scheduleLog.getDataKeyId()),
                eq(scheduleLog.getEncryptedPackage()),
                eq(scheduleLog.getAadJson()),
                eq(WARD_ID),
                eq(CommonCryptoService.OWNER_TYPE_USER),
                any(byte[].class),
                eq(MedicationSchedulePayload.class)
        )).thenReturn(schedulePayload());
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndSourceTableAndOccurredAtBetweenOrderByOccurredAtAsc(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_log"),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(logEvents);
        for (int i = 0; i < logEvents.size(); i++) {
            EncryptedActivityLog event = logEvents.get(i);
            MedicationLogPayload payload = logs.get(i);
            when(commonCryptoService.decryptActivityLogPayload(
                    eq(event.getDataKeyId()),
                    eq(event.getEncryptedPackage()),
                    eq(event.getAadJson()),
                    eq(WARD_ID),
                    eq(CommonCryptoService.OWNER_TYPE_USER),
                    any(byte[].class),
                    eq(MedicationLogPayload.class)
            )).thenReturn(payload);
        }

        return medicationAnalysisService.analyzeWardMedication(WARD_ID, analysisDate).get(0);
    }

    private MedicationSchedule schedule() {
        MedicationSchedule schedule = MedicationSchedule.builder()
                .wardId(WARD_ID)
                .build();
        ReflectionTestUtils.setField(schedule, "id", SCHEDULE_ID);
        return schedule;
    }

    private MedicationSchedulePayload schedulePayload() {
        return new MedicationSchedulePayload(
                "CREATED",
                SCHEDULE_ID,
                "morning pill",
                LocalTime.of(8, 0),
                10,
                30,
                MedicationScheduleType.DAILY,
                null,
                true
        );
    }

    private MedicationLogPayload logPayload(LocalDateTime takenAt) {
        return new MedicationLogPayload(
                SCHEDULE_ID,
                LocalDateTime.of(takenAt.toLocalDate(), LocalTime.of(8, 0)),
                takenAt,
                "morning pill",
                MedicationLogSource.USER_INPUT,
                10,
                30
        );
    }

    private EncryptedActivityLog encryptedLog(String sourceTable, Long sourceId, LocalDateTime occurredAt) {
        return EncryptedActivityLog.builder()
                .wardId(WARD_ID)
                .dataKeyId("datakey-test")
                .eventType(ActivityEventType.MEDICATION_EVENT)
                .sourceTable(sourceTable)
                .sourceId(sourceId)
                .occurredAt(occurredAt)
                .encryptedPackage(new byte[] {1, 2, 3})
                .aadJson("{\"ward_id\":1}")
                .build();
    }

}
