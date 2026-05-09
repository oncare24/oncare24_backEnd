package com.oncare.oncare24.inactivity.service;

import com.oncare.oncare24.inactivity.dto.InactivityAnalysisResult;
import com.oncare.oncare24.inactivity.entity.InactivityAnalysisStatus;
import com.oncare.oncare24.inactivity.entity.InactivityDetectionRule;
import com.oncare.oncare24.inactivity.repository.InactivityDetectionRuleRepository;
import com.oncare.oncare24.location.entity.DeviceStatus;
import com.oncare.oncare24.location.entity.LocationReport;
import com.oncare.oncare24.location.entity.LocationReportSource;
import com.oncare.oncare24.location.repository.DeviceStatusRepository;
import com.oncare.oncare24.location.repository.LocationReportRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InactivityAnalysisServiceTest {

    private static final Long WARD_ID = 1L;
    private static final Long SECOND_WARD_ID = 2L;
    private static final Long RULE_ID = 10L;
    private static final LocalDateTime ANALYSIS_AT = LocalDateTime.of(2026, 5, 8, 12, 0);

    @Mock
    private InactivityDetectionRuleRepository inactivityDetectionRuleRepository;

    @Mock
    private LocationReportRepository locationReportRepository;

    @Mock
    private DeviceStatusRepository deviceStatusRepository;

    @Mock
    private UserRepository userRepository;

    private InactivityAnalysisService inactivityAnalysisService;

    @BeforeEach
    void setUp() {
        inactivityAnalysisService = new InactivityAnalysisService(
                inactivityDetectionRuleRepository,
                locationReportRepository,
                deviceStatusRepository,
                userRepository
        );
    }

    @Test
    void analyzeWardInactivity_returnsNormalWhenReliableMovementIsRecent() {
        LocationReport first = report(ANALYSIS_AT.minusMinutes(60), 37.0, 127.0, 10.0);
        LocationReport second = report(ANALYSIS_AT.minusMinutes(10), 37.0, 127.001, 10.0);

        InactivityAnalysisResult result = analyzeWardWithReports(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.NORMAL);
        assertThat(result.lastReliableMovementAt()).isEqualTo(second.getCreatedAt());
        assertThat(result.inactiveMinutes()).isEqualTo(10);
        assertThat(result.reliableReportCount()).isEqualTo(2);
    }

    @Test
    void analyzeWardInactivity_returnsInactiveWarningWhenNoReliableMovementBeyondWarningThreshold() {
        LocationReport first = report(ANALYSIS_AT.minusMinutes(300), 37.0, 127.0, 20.0);
        LocationReport second = report(ANALYSIS_AT.minusMinutes(10), 37.0, 127.0001, 25.0);

        InactivityAnalysisResult result = analyzeWardWithReports(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.INACTIVE_WARNING);
        assertThat(result.lastReliableMovementAt()).isEqualTo(first.getCreatedAt());
        assertThat(result.inactiveMinutes()).isEqualTo(300);
    }

    @Test
    void analyzeWardInactivity_returnsInactiveDangerWhenNoReliableMovementBeyondDangerThreshold() {
        LocationReport first = report(ANALYSIS_AT.minusMinutes(500), 37.0, 127.0, 20.0);
        LocationReport second = report(ANALYSIS_AT.minusMinutes(10), 37.0, 127.0001, 25.0);

        InactivityAnalysisResult result = analyzeWardWithReports(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.INACTIVE_DANGER);
        assertThat(result.inactiveMinutes()).isEqualTo(500);
    }

    @Test
    void analyzeWardInactivity_returnsStaleLocationWarningWhenLatestReportIsOlderThanWarningThreshold() {
        LocationReport latest = report(ANALYSIS_AT.minusMinutes(180), 37.0, 127.0, 20.0);

        InactivityAnalysisResult result = analyzeWardWithLatestOnly(rule(WARD_ID), latest);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.STALE_LOCATION_WARNING);
        assertThat(result.staleLocationMinutes()).isEqualTo(180);
    }

    @Test
    void analyzeWardInactivity_returnsStaleLocationDangerWhenLatestReportIsOlderThanDangerThreshold() {
        LocationReport latest = report(ANALYSIS_AT.minusMinutes(400), 37.0, 127.0, 20.0);

        InactivityAnalysisResult result = analyzeWardWithLatestOnly(rule(WARD_ID), latest);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.STALE_LOCATION_DANGER);
        assertThat(result.staleLocationMinutes()).isEqualTo(400);
    }

    @Test
    void analyzeWardInactivity_returnsLocationUnreliableWhenAllReportsExceedMaxAccuracy() {
        LocationReport first = report(ANALYSIS_AT.minusMinutes(60), 37.0, 127.0, 150.0);
        LocationReport second = report(ANALYSIS_AT.minusMinutes(10), 37.0, 127.001, 200.0);

        InactivityAnalysisResult result = analyzeWardWithReports(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.LOCATION_UNRELIABLE);
        assertThat(result.usedReportCount()).isEqualTo(2);
        assertThat(result.reliableReportCount()).isZero();
    }

    @Test
    void analyzeWardInactivity_returnsDeviceDisconnectedBeforeLocationAnalysis() {
        InactivityDetectionRule rule = rule(WARD_ID);
        DeviceStatus disconnected = DeviceStatus.builder()
                .userId(WARD_ID)
                .build();
        disconnected.markDisconnected();

        stubWard();
        when(inactivityDetectionRuleRepository.findByWardIdAndActiveTrue(WARD_ID)).thenReturn(Optional.of(rule));
        when(deviceStatusRepository.findByUserId(WARD_ID)).thenReturn(Optional.of(disconnected));

        InactivityAnalysisResult result = inactivityAnalysisService.analyzeWardInactivity(WARD_ID, ANALYSIS_AT);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.DEVICE_DISCONNECTED);
        verifyNoInteractions(locationReportRepository);
    }

    @Test
    void analyzeWardInactivity_doesNotRecognizeSmallCoordinateChangeInsideGpsErrorAsMovement() {
        LocationReport first = report(ANALYSIS_AT.minusMinutes(300), 37.0, 127.0, 20.0);
        LocationReport second = report(ANALYSIS_AT.minusMinutes(10), 37.0, 127.0001, 25.0);

        InactivityAnalysisResult result = analyzeWardWithReports(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.INACTIVE_WARNING);
        assertThat(result.lastReliableMovementAt()).isEqualTo(first.getCreatedAt());
    }

    @Test
    void analyzeWardInactivity_recognizesMovementBeyondGpsErrorAndMinimumDistance() {
        LocationReport first = report(ANALYSIS_AT.minusMinutes(300), 37.0, 127.0, 10.0);
        LocationReport second = report(ANALYSIS_AT.minusMinutes(10), 37.0, 127.001, 10.0);

        InactivityAnalysisResult result = analyzeWardWithReports(rule(WARD_ID), List.of(first, second), second);

        assertThat(result.status()).isEqualTo(InactivityAnalysisStatus.NORMAL);
        assertThat(result.lastReliableMovementAt()).isEqualTo(second.getCreatedAt());
        assertThat(result.inactiveMinutes()).isEqualTo(10);
    }

    @Test
    void analyzeAllActiveWardInactivity_returnsResultsForAllActiveRules() {
        InactivityDetectionRule firstRule = rule(WARD_ID);
        InactivityDetectionRule secondRule = rule(SECOND_WARD_ID, 11L);
        LocationReport firstLatest = report(ANALYSIS_AT.minusMinutes(10), 37.0, 127.001, 10.0);
        LocationReport secondLatest = report(ANALYSIS_AT.minusMinutes(400), 37.0, 127.0, 20.0);

        when(inactivityDetectionRuleRepository.findByActiveTrueOrderByWardIdAsc())
                .thenReturn(List.of(firstRule, secondRule));
        when(deviceStatusRepository.findByUserId(WARD_ID)).thenReturn(Optional.empty());
        when(deviceStatusRepository.findByUserId(SECOND_WARD_ID)).thenReturn(Optional.empty());
        when(locationReportRepository.findFirstByUserIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                WARD_ID,
                ANALYSIS_AT
        )).thenReturn(Optional.of(firstLatest));
        when(locationReportRepository.findFirstByUserIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                SECOND_WARD_ID,
                ANALYSIS_AT
        )).thenReturn(Optional.of(secondLatest));
        when(locationReportRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(WARD_ID),
                any(LocalDateTime.class),
                eq(ANALYSIS_AT)
        )).thenReturn(List.of(
                report(ANALYSIS_AT.minusMinutes(60), 37.0, 127.0, 10.0),
                firstLatest
        ));

        List<InactivityAnalysisResult> results =
                inactivityAnalysisService.analyzeAllActiveWardInactivity(ANALYSIS_AT);

        assertThat(results).extracting(InactivityAnalysisResult::status)
                .containsExactly(
                        InactivityAnalysisStatus.NORMAL,
                        InactivityAnalysisStatus.STALE_LOCATION_DANGER
                );
    }


    private InactivityAnalysisResult analyzeWardWithReports(
            InactivityDetectionRule rule,
            List<LocationReport> reports,
            LocationReport latestReport
    ) {
        stubWard();
        when(inactivityDetectionRuleRepository.findByWardIdAndActiveTrue(WARD_ID)).thenReturn(Optional.of(rule));
        when(deviceStatusRepository.findByUserId(WARD_ID)).thenReturn(Optional.empty());
        when(locationReportRepository.findFirstByUserIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                WARD_ID,
                ANALYSIS_AT
        )).thenReturn(Optional.of(latestReport));
        when(locationReportRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(WARD_ID),
                any(LocalDateTime.class),
                eq(ANALYSIS_AT)
        )).thenReturn(reports);

        return inactivityAnalysisService.analyzeWardInactivity(WARD_ID, ANALYSIS_AT);
    }

    private InactivityAnalysisResult analyzeWardWithLatestOnly(
            InactivityDetectionRule rule,
            LocationReport latestReport
    ) {
        stubWard();
        when(inactivityDetectionRuleRepository.findByWardIdAndActiveTrue(WARD_ID)).thenReturn(Optional.of(rule));
        when(deviceStatusRepository.findByUserId(WARD_ID)).thenReturn(Optional.empty());
        when(locationReportRepository.findFirstByUserIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                WARD_ID,
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

    private LocationReport report(LocalDateTime createdAt, double latitude, double longitude, double accuracy) {
        LocationReport report = LocationReport.builder()
                .userId(WARD_ID)
                .latitude(BigDecimal.valueOf(latitude))
                .longitude(BigDecimal.valueOf(longitude))
                .accuracy(accuracy)
                .reportSource(LocationReportSource.BACKGROUND_SCHEDULED)
                .build();
        ReflectionTestUtils.setField(report, "createdAt", createdAt);
        ReflectionTestUtils.setField(report, "updatedAt", createdAt);
        return report;
    }

}
