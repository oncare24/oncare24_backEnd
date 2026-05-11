package com.oncare.oncare24.inactivity.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.AnalysisType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.analysis.service.AnalysisStateService;
import com.oncare.oncare24.inactivity.dto.InactivityAnalysisResult;
import com.oncare.oncare24.inactivity.entity.InactivityAnalysisStatus;
import com.oncare.oncare24.inactivity.entity.InactivityDetectionRule;
import com.oncare.oncare24.inactivity.repository.InactivityDetectionRuleRepository;
import com.oncare.oncare24.location.dto.DeviceStatusSourcePayload;
import com.oncare.oncare24.location.dto.LocationSourcePayload;
import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.entity.LocationReportSource;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InactivityAnalysisServiceTest {

    private static final Long WARD_ID = 1L;
    private static final Long SECOND_WARD_ID = 2L;
    private static final Long RULE_ID = 10L;
    private static final LocalDateTime ANALYSIS_AT = LocalDateTime.of(2026, 5, 8, 12, 0);
    private static final byte[] WARD_PRIVATE_KEY = new byte[]{1, 2, 3};

    @Mock
    private InactivityDetectionRuleRepository inactivityDetectionRuleRepository;

    @Mock
    private EncryptedActivityLogRepository encryptedActivityLogRepository;

    @Mock
    private CommonCryptoService commonCryptoService;

    @Mock
    private MlKemKeyProvisionService mlKemKeyProvisionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AnalysisStateService analysisStateService;

    private InactivityAnalysisService inactivityAnalysisService;

    @BeforeEach
    void setUp() {
        inactivityAnalysisService = new InactivityAnalysisService(
                inactivityDetectionRuleRepository,
                encryptedActivityLogRepository,
                commonCryptoService,
                mlKemKeyProvisionService,
                userRepository,
                analysisStateService
        );
    }

    @Test
    void analyzeWardInactivity_returnsNormalWhenReliableMovementIsRecent() {
        EncryptedActivityLog first = locationLog(1L, WARD_ID, ANALYSIS_AT.minusMinutes(60), 37.0, 127.0, 10.0);
        EncryptedActivityLog second = locationLog(2L, WARD_ID, ANALYSIS_AT.minusMinutes(10), 37.0, 127.001, 10.0);

        InactivityAnalysisResult result = analyzeWardWithLocationLogs(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.NORMAL);
        assertThat(result.lastReliableMovementAt()).isEqualTo(second.getOccurredAt());
        assertThat(result.inactiveMinutes()).isEqualTo(10);
        assertThat(result.reliableReportCount()).isEqualTo(2);
        verifyInactivityStateSaved(0);
    }

    @Test
    void analyzeWardInactivity_returnsInactiveWarningWhenNoReliableMovementBeyondWarningThreshold() {
        EncryptedActivityLog first = locationLog(1L, WARD_ID, ANALYSIS_AT.minusMinutes(300), 37.0, 127.0, 20.0);
        EncryptedActivityLog second = locationLog(2L, WARD_ID, ANALYSIS_AT.minusMinutes(10), 37.0, 127.0001, 25.0);

        InactivityAnalysisResult result = analyzeWardWithLocationLogs(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.INACTIVE_WARNING);
        assertThat(result.lastReliableMovementAt()).isEqualTo(first.getOccurredAt());
        assertThat(result.inactiveMinutes()).isEqualTo(300);
        verifyInactivityStateSaved(1);
    }

    @Test
    void analyzeWardInactivity_returnsInactiveDangerWhenNoReliableMovementBeyondDangerThreshold() {
        EncryptedActivityLog first = locationLog(1L, WARD_ID, ANALYSIS_AT.minusMinutes(500), 37.0, 127.0, 20.0);
        EncryptedActivityLog second = locationLog(2L, WARD_ID, ANALYSIS_AT.minusMinutes(10), 37.0, 127.0001, 25.0);

        InactivityAnalysisResult result = analyzeWardWithLocationLogs(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.INACTIVE_DANGER);
        assertThat(result.inactiveMinutes()).isEqualTo(500);
        verifyInactivityStateSaved(1);
    }

    @Test
    void analyzeWardInactivity_returnsStaleLocationWarningWhenLatestReportIsOlderThanWarningThreshold() {
        EncryptedActivityLog latest = locationLog(1L, WARD_ID, ANALYSIS_AT.minusMinutes(180), 37.0, 127.0, 20.0);

        InactivityAnalysisResult result = analyzeWardWithLatestOnly(rule(WARD_ID), latest);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.STALE_LOCATION_WARNING);
        assertThat(result.staleLocationMinutes()).isEqualTo(180);
        verifyInactivityStateSaved(2);
    }

    @Test
    void analyzeWardInactivity_returnsStaleLocationDangerWhenLatestReportIsOlderThanDangerThreshold() {
        EncryptedActivityLog latest = locationLog(1L, WARD_ID, ANALYSIS_AT.minusMinutes(400), 37.0, 127.0, 20.0);

        InactivityAnalysisResult result = analyzeWardWithLatestOnly(rule(WARD_ID), latest);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.STALE_LOCATION_DANGER);
        assertThat(result.staleLocationMinutes()).isEqualTo(400);
        verifyInactivityStateSaved(2);
    }

    @Test
    void analyzeWardInactivity_returnsLocationUnreliableWhenAllReportsExceedMaxAccuracy() {
        EncryptedActivityLog first = locationLog(1L, WARD_ID, ANALYSIS_AT.minusMinutes(60), 37.0, 127.0, 150.0);
        EncryptedActivityLog second = locationLog(2L, WARD_ID, ANALYSIS_AT.minusMinutes(10), 37.0, 127.001, 200.0);

        InactivityAnalysisResult result = analyzeWardWithLocationLogs(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.LOCATION_UNRELIABLE);
        assertThat(result.usedReportCount()).isEqualTo(2);
        assertThat(result.reliableReportCount()).isZero();
        verifyInactivityStateSaved(2);
    }

    @Test
    void analyzeWardInactivity_returnsDeviceDisconnectedBeforeLocationAnalysis() {
        InactivityDetectionRule rule = rule(WARD_ID);
        EncryptedActivityLog disconnected = deviceLog(3L, WARD_ID, ANALYSIS_AT.minusMinutes(1), DeviceState.DISCONNECTED);

        stubWard();
        stubPrivateKey(WARD_ID);
        when(inactivityDetectionRuleRepository.findByWardIdAndActiveTrue(WARD_ID)).thenReturn(Optional.of(rule));
        when(encryptedActivityLogRepository.findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                WARD_ID,
                ActivityEventType.DEVICE_EVENT,
                "device_status",
                ANALYSIS_AT
        )).thenReturn(Optional.of(disconnected));

        InactivityAnalysisResult result = inactivityAnalysisService.analyzeWardInactivity(WARD_ID, ANALYSIS_AT);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.DEVICE_DISCONNECTED);
        verifyInactivityStateSaved(2);
        verify(encryptedActivityLogRepository, never())
                .findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                        WARD_ID,
                        ActivityEventType.LOCATION_EVENT,
                        "location_report",
                        ANALYSIS_AT
                );
    }

    @Test
    void analyzeWardInactivity_doesNotRecognizeSmallCoordinateChangeInsideGpsErrorAsMovement() {
        EncryptedActivityLog first = locationLog(1L, WARD_ID, ANALYSIS_AT.minusMinutes(300), 37.0, 127.0, 20.0);
        EncryptedActivityLog second = locationLog(2L, WARD_ID, ANALYSIS_AT.minusMinutes(10), 37.0, 127.0001, 25.0);

        InactivityAnalysisResult result = analyzeWardWithLocationLogs(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.INACTIVE_WARNING);
        assertThat(result.lastReliableMovementAt()).isEqualTo(first.getOccurredAt());
        verifyInactivityStateSaved(1);
    }

    @Test
    void analyzeWardInactivity_recognizesMovementBeyondGpsErrorAndMinimumDistance() {
        EncryptedActivityLog first = locationLog(1L, WARD_ID, ANALYSIS_AT.minusMinutes(300), 37.0, 127.0, 10.0);
        EncryptedActivityLog second = locationLog(2L, WARD_ID, ANALYSIS_AT.minusMinutes(10), 37.0, 127.001, 10.0);

        InactivityAnalysisResult result = analyzeWardWithLocationLogs(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.NORMAL);
        assertThat(result.lastReliableMovementAt()).isEqualTo(second.getOccurredAt());
        assertThat(result.inactiveMinutes()).isEqualTo(10);
        verifyInactivityStateSaved(0);
    }

    @Test
    void analyzeAllActiveWardInactivity_returnsResultsForAllActiveRules() {
        InactivityDetectionRule firstRule = rule(WARD_ID);
        InactivityDetectionRule secondRule = rule(SECOND_WARD_ID, 11L);
        EncryptedActivityLog firstOld = locationLog(1L, WARD_ID, ANALYSIS_AT.minusMinutes(60), 37.0, 127.0, 10.0);
        EncryptedActivityLog firstLatest = locationLog(2L, WARD_ID, ANALYSIS_AT.minusMinutes(10), 37.0, 127.001, 10.0);
        EncryptedActivityLog secondLatest = locationLog(3L, SECOND_WARD_ID, ANALYSIS_AT.minusMinutes(400), 37.0, 127.0, 20.0);

        when(inactivityDetectionRuleRepository.findByActiveTrueOrderByWardIdAsc())
                .thenReturn(List.of(firstRule, secondRule));
        stubPrivateKey(WARD_ID);
        stubPrivateKey(SECOND_WARD_ID);
        when(encryptedActivityLogRepository.findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                WARD_ID, ActivityEventType.DEVICE_EVENT, "device_status", ANALYSIS_AT
        )).thenReturn(Optional.empty());
        when(encryptedActivityLogRepository.findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                SECOND_WARD_ID, ActivityEventType.DEVICE_EVENT, "device_status", ANALYSIS_AT
        )).thenReturn(Optional.empty());
        when(encryptedActivityLogRepository.findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                WARD_ID, ActivityEventType.LOCATION_EVENT, "location_report", ANALYSIS_AT
        )).thenReturn(Optional.of(firstLatest));
        when(encryptedActivityLogRepository.findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                SECOND_WARD_ID, ActivityEventType.LOCATION_EVENT, "location_report", ANALYSIS_AT
        )).thenReturn(Optional.of(secondLatest));
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndSourceTableAndOccurredAtBetweenOrderByOccurredAtAsc(
                eq(WARD_ID), eq(ActivityEventType.LOCATION_EVENT), eq("location_report"), any(LocalDateTime.class), eq(ANALYSIS_AT)
        )).thenReturn(List.of(firstOld, firstLatest));

        List<InactivityAnalysisResult> results =
                inactivityAnalysisService.analyzeAllActiveWardInactivity(ANALYSIS_AT);

        assertThat(results).extracting(InactivityAnalysisResult::status)
                .containsExactly(
                        InactivityAnalysisStatus.NORMAL,
                        InactivityAnalysisStatus.STALE_LOCATION_DANGER
                );
        verify(analysisStateService).upsertLatestState(
                WARD_ID,
                AnalysisType.INACTIVITY,
                0,
                ANALYSIS_AT
        );
        verify(analysisStateService).upsertLatestState(
                SECOND_WARD_ID,
                AnalysisType.INACTIVITY,
                2,
                ANALYSIS_AT
        );
    }

    private void verifyInactivityStateSaved(int statusCode) {
        verify(analysisStateService).upsertLatestState(
                WARD_ID,
                AnalysisType.INACTIVITY,
                statusCode,
                ANALYSIS_AT
        );
    }

    private InactivityAnalysisResult analyzeWardWithLocationLogs(
            InactivityDetectionRule rule,
            List<EncryptedActivityLog> reports,
            EncryptedActivityLog latestReport
    ) {
        stubWard();
        stubPrivateKey(WARD_ID);
        when(inactivityDetectionRuleRepository.findByWardIdAndActiveTrue(WARD_ID)).thenReturn(Optional.of(rule));
        when(encryptedActivityLogRepository.findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                WARD_ID,
                ActivityEventType.DEVICE_EVENT,
                "device_status",
                ANALYSIS_AT
        )).thenReturn(Optional.empty());
        when(encryptedActivityLogRepository.findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                WARD_ID,
                ActivityEventType.LOCATION_EVENT,
                "location_report",
                ANALYSIS_AT
        )).thenReturn(Optional.of(latestReport));
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndSourceTableAndOccurredAtBetweenOrderByOccurredAtAsc(
                eq(WARD_ID),
                eq(ActivityEventType.LOCATION_EVENT),
                eq("location_report"),
                any(LocalDateTime.class),
                eq(ANALYSIS_AT)
        )).thenReturn(reports);

        return inactivityAnalysisService.analyzeWardInactivity(WARD_ID, ANALYSIS_AT);
    }

    private InactivityAnalysisResult analyzeWardWithLatestOnly(
            InactivityDetectionRule rule,
            EncryptedActivityLog latestReport
    ) {
        stubWard();
        stubPrivateKey(WARD_ID);
        when(inactivityDetectionRuleRepository.findByWardIdAndActiveTrue(WARD_ID)).thenReturn(Optional.of(rule));
        when(encryptedActivityLogRepository.findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                WARD_ID,
                ActivityEventType.DEVICE_EVENT,
                "device_status",
                ANALYSIS_AT
        )).thenReturn(Optional.empty());
        when(encryptedActivityLogRepository.findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                WARD_ID,
                ActivityEventType.LOCATION_EVENT,
                "location_report",
                ANALYSIS_AT
        )).thenReturn(Optional.of(latestReport));

        return inactivityAnalysisService.analyzeWardInactivity(WARD_ID, ANALYSIS_AT);
    }

    private void stubWard() {
        User ward = User.builder()
                .phone("01012345678")
                .password("encoded-password")
                .name("ward")
                .role(UserRole.ELDER)
                .build();
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
    }

    private void stubPrivateKey(Long wardId) {
        when(mlKemKeyProvisionService.readPrivateKey(wardId)).thenReturn(WARD_PRIVATE_KEY);
    }

    private InactivityDetectionRule rule(Long wardId) {
        return rule(wardId, RULE_ID);
    }

    private InactivityDetectionRule rule(Long wardId, Long ruleId) {
        InactivityDetectionRule rule = InactivityDetectionRule.builder()
                .wardId(wardId)
                .warningMinutes(240)
                .dangerMinutes(480)
                .staleLocationWarningMinutes(120)
                .staleLocationDangerMinutes(360)
                .minMovementMeters(30.0)
                .maxAccuracyMeters(100.0)
                .build();
        ReflectionTestUtils.setField(rule, "id", ruleId);
        return rule;
    }

    private EncryptedActivityLog locationLog(
            Long sourceId,
            Long wardId,
            LocalDateTime occurredAt,
            double latitude,
            double longitude,
            double accuracy
    ) {
        EncryptedActivityLog log = encryptedLog(
                sourceId,
                wardId,
                ActivityEventType.LOCATION_EVENT,
                "location_report",
                occurredAt
        );
        LocationSourcePayload payload = new LocationSourcePayload(
                wardId,
                BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude),
                accuracy,
                occurredAt,
                "location_report",
                sourceId,
                ActivityEventType.LOCATION_EVENT,
                LocationReportSource.BACKGROUND_SCHEDULED
        );
        when(commonCryptoService.decryptActivityLogPayload(
                eq(log.getDataKeyId()),
                eq(log.getEncryptedPackage()),
                eq(log.getAadJson()),
                eq(wardId),
                eq(CommonCryptoService.OWNER_TYPE_USER),
                eq(WARD_PRIVATE_KEY),
                eq(LocationSourcePayload.class)
        )).thenReturn(payload);
        return log;
    }

    private EncryptedActivityLog deviceLog(Long sourceId, Long wardId, LocalDateTime occurredAt, DeviceState state) {
        EncryptedActivityLog log = encryptedLog(
                sourceId,
                wardId,
                ActivityEventType.DEVICE_EVENT,
                "device_status",
                occurredAt
        );
        DeviceStatusSourcePayload payload = new DeviceStatusSourcePayload(
                wardId,
                state,
                occurredAt.minusMinutes(30),
                state == DeviceState.DISCONNECTED ? occurredAt : null,
                occurredAt,
                "device_status",
                sourceId,
                ActivityEventType.DEVICE_EVENT
        );
        when(commonCryptoService.decryptActivityLogPayload(
                eq(log.getDataKeyId()),
                eq(log.getEncryptedPackage()),
                eq(log.getAadJson()),
                eq(wardId),
                eq(CommonCryptoService.OWNER_TYPE_USER),
                eq(WARD_PRIVATE_KEY),
                eq(DeviceStatusSourcePayload.class)
        )).thenReturn(payload);
        return log;
    }

    private EncryptedActivityLog encryptedLog(
            Long sourceId,
            Long wardId,
            ActivityEventType eventType,
            String sourceTable,
            LocalDateTime occurredAt
    ) {
        return EncryptedActivityLog.builder()
                .wardId(wardId)
                .dataKeyId("key-" + eventType + "-" + wardId + "-" + sourceId)
                .eventType(eventType)
                .sourceTable(sourceTable)
                .sourceId(sourceId)
                .occurredAt(occurredAt)
                .encryptedPackage(new byte[]{9, sourceId.byteValue()})
                .aadJson("{\"ward_id\":" + wardId + "}")
                .build();
    }
}
