package com.oncare.oncare24.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.service.AnalysisRefreshService;
import com.oncare.oncare24.analysis.service.EncryptedSourceEventService;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.location.dto.DeviceStatusSourcePayload;
import com.oncare.oncare24.location.dto.LocationReportRequest;
import com.oncare.oncare24.location.dto.LocationReportResponse;
import com.oncare.oncare24.location.dto.LocationSourcePayload;
import com.oncare.oncare24.location.entity.DeviceStatus;
import com.oncare.oncare24.location.entity.LocationReport;
import com.oncare.oncare24.location.entity.LocationReportSource;
import com.oncare.oncare24.location.repository.DeviceStatusRepository;
import com.oncare.oncare24.location.repository.LocationReportRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class LocationReportServiceTest {
    private static final Long WARD_ID = 1L;
    private static final Long REPORT_ID = 100L;

    @Mock
    private GuardianWardRepository guardianWardRepository;

    @Mock
    private LocationReportRepository locationReportRepository;

    @Mock
    private DeviceStatusRepository deviceStatusRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GeofencingService geofencingService;

    @Mock
    private EncryptedSourceEventService encryptedSourceEventService;

    @Mock
    private AnalysisRefreshService analysisRefreshService;

    private LocationReportService locationReportService;

    @BeforeEach
    void setUp() {
        locationReportService = new LocationReportService(
                guardianWardRepository,
                locationReportRepository,
                deviceStatusRepository,
                userRepository,
                geofencingService,
                encryptedSourceEventService,
                analysisRefreshService
        );
    }

    @Test
    void reportSavesEncryptedLocationSourceEvent() {
        User ward = User.builder()
                .phone("01011111111")
                .password("encoded")
                .name("ward")
                .role(UserRole.ELDER)
                .build();
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
        when(locationReportRepository.save(any(LocationReport.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), REPORT_ID));
        when(deviceStatusRepository.findByUserId(WARD_ID)).thenReturn(Optional.of(DeviceStatus.builder().userId(WARD_ID).build()));

        LocationReportResponse response = locationReportService.report(
                WARD_ID,
                new LocationReportRequest(
                        BigDecimal.valueOf(37.5),
                        BigDecimal.valueOf(127.0),
                        10.0,
                        LocationReportSource.BACKGROUND_SCHEDULED
                )
        );

        assertThat(response.stored()).isTrue();
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(encryptedSourceEventService).saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.LOCATION_EVENT),
                eq("location_report"),
                eq(REPORT_ID),
                any(),
                payloadCaptor.capture()
        );
        LocationSourcePayload capturedPayload = (LocationSourcePayload) payloadCaptor.getValue();
        assertThat(capturedPayload.sourceId()).isEqualTo(REPORT_ID);
        assertThat(capturedPayload.accuracy()).isEqualTo(10.0);
        assertThat(capturedPayload.reportSource()).isEqualTo(LocationReportSource.BACKGROUND_SCHEDULED);

        ArgumentCaptor<Object> devicePayloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(encryptedSourceEventService).saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.DEVICE_EVENT),
                eq("device_status"),
                any(),
                any(),
                devicePayloadCaptor.capture()
        );
        DeviceStatusSourcePayload capturedDevicePayload = (DeviceStatusSourcePayload) devicePayloadCaptor.getValue();
        assertThat(capturedDevicePayload.wardId()).isEqualTo(WARD_ID);
        assertThat(capturedDevicePayload.deviceStatus()).isEqualTo(com.oncare.oncare24.location.entity.DeviceState.ACTIVE);
        verify(analysisRefreshService, times(2)).refreshInactivityState(WARD_ID);
    }

    private LocationReport withId(LocationReport report, Long id) {
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }
}
