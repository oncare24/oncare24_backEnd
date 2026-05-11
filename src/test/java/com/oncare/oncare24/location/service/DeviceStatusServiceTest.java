package com.oncare.oncare24.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.service.AnalysisRefreshService;
import com.oncare.oncare24.analysis.service.EncryptedSourceEventService;
import com.oncare.oncare24.location.dto.DeviceStatusSourcePayload;
import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.entity.DeviceStatus;
import com.oncare.oncare24.location.repository.DeviceStatusRepository;
import com.oncare.oncare24.notification.service.NotificationService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DeviceStatusServiceTest {
    private static final Long WARD_ID = 1L;
    private static final Long DEVICE_STATUS_ID = 100L;

    @Mock
    private DeviceStatusRepository deviceStatusRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EncryptedSourceEventService encryptedSourceEventService;

    @Mock
    private AnalysisRefreshService analysisRefreshService;

    private DeviceStatusService deviceStatusService;

    @BeforeEach
    void setUp() {
        deviceStatusService = new DeviceStatusService(
                deviceStatusRepository,
                userRepository,
                notificationService,
                encryptedSourceEventService,
                analysisRefreshService
        );
    }

    @Test
    void detectDisconnectedDevicesSavesEncryptedDeviceSourceEvent() {
        DeviceStatus device = DeviceStatus.builder().userId(WARD_ID).build();
        LocalDateTime lastReportAt = LocalDateTime.of(2026, 5, 9, 8, 0);
        device.onLocationReported(lastReportAt);
        ReflectionTestUtils.setField(device, "id", DEVICE_STATUS_ID);
        User ward = User.builder()
                .phone("01011111111")
                .password("encoded")
                .name("ward")
                .role(UserRole.ELDER)
                .build();

        when(deviceStatusRepository.findByStateAndLastReportAtBefore(eq(DeviceState.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(device));
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));

        deviceStatusService.detectDisconnectedDevices();

        assertThat(device.getState()).isEqualTo(DeviceState.DISCONNECTED);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(encryptedSourceEventService).saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.DEVICE_EVENT),
                eq("device_status"),
                eq(DEVICE_STATUS_ID),
                any(),
                payloadCaptor.capture()
        );
        DeviceStatusSourcePayload capturedPayload = (DeviceStatusSourcePayload) payloadCaptor.getValue();
        assertThat(capturedPayload.sourceId()).isEqualTo(DEVICE_STATUS_ID);
        assertThat(capturedPayload.deviceStatus()).isEqualTo(DeviceState.DISCONNECTED);
        assertThat(capturedPayload.disconnectedAt()).isNotNull();
        verify(analysisRefreshService).refreshInactivityState(WARD_ID);
    }
}
