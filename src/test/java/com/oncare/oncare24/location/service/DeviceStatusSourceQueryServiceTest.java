package com.oncare.oncare24.location.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.location.dto.DeviceStatusSourcePayload;
import com.oncare.oncare24.location.dto.DeviceStatusSourceResponse;
import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.security.crypto.service.CommonCryptoService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceStatusSourceQueryServiceTest {

    private static final Long WARD_ID = 1L;
    private static final Long GUARDIAN_ID = 2L;
    private static final byte[] WARD_PRIVATE_KEY = new byte[]{1, 2, 3};

    private EncryptedActivityLogRepository encryptedActivityLogRepository;
    private CommonCryptoService commonCryptoService;
    private MlKemKeyProvisionService mlKemKeyProvisionService;
    private UserRepository userRepository;
    private GuardianWardRepository guardianWardRepository;
    private LocationSourceQueryService locationSourceQueryService;

    @BeforeEach
    void setUp() {
        encryptedActivityLogRepository = mock(EncryptedActivityLogRepository.class);
        commonCryptoService = mock(CommonCryptoService.class);
        mlKemKeyProvisionService = mock(MlKemKeyProvisionService.class);
        userRepository = mock(UserRepository.class);
        guardianWardRepository = mock(GuardianWardRepository.class);
        locationSourceQueryService = new LocationSourceQueryService(
                encryptedActivityLogRepository,
                commonCryptoService,
                mlKemKeyProvisionService,
                userRepository,
                guardianWardRepository
        );
    }

    @Test
    void findDeviceStatusRecordsDecryptsActiveAndDisconnectedEventsAndSortsByReportedAt() {
        stubElderAccess();
        stubPrivateKey();
        LocalDateTime from = LocalDateTime.of(2026, 5, 11, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 5, 12, 0, 0);
        EncryptedActivityLog disconnected = encryptedLog(101L, LocalDateTime.of(2026, 5, 11, 22, 5));
        EncryptedActivityLog active = encryptedLog(100L, LocalDateTime.of(2026, 5, 11, 21, 30));
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndOccurredAtBetweenOrderByOccurredAtDesc(
                WARD_ID,
                ActivityEventType.DEVICE_EVENT,
                from,
                to
        )).thenReturn(List.of(disconnected, active));
        stubDecrypt(active, devicePayload(100L, DeviceState.ACTIVE, LocalDateTime.of(2026, 5, 11, 21, 30), null));
        stubDecrypt(disconnected, devicePayload(101L, DeviceState.DISCONNECTED, LocalDateTime.of(2026, 5, 11, 22, 5), LocalDateTime.of(2026, 5, 11, 22, 5)));

        List<DeviceStatusSourceResponse> responses =
                locationSourceQueryService.findDeviceStatusRecords(WARD_ID, WARD_ID, from, to);

        assertThat(responses).extracting(DeviceStatusSourceResponse::deviceStatus)
                .containsExactly(DeviceState.ACTIVE, DeviceState.DISCONNECTED);
        assertThat(responses.get(0).lastActiveAt()).isEqualTo(LocalDateTime.of(2026, 5, 11, 21, 30));
        assertThat(responses.get(0).disconnectedAt()).isNull();
        assertThat(responses.get(1).disconnectedAt()).isEqualTo(LocalDateTime.of(2026, 5, 11, 22, 5));
    }

    @Test
    void acceptedGuardianCanFindDeviceStatusRecords() {
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
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(WARD_ID),
                eq(ActivityEventType.DEVICE_EVENT),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of());

        List<DeviceStatusSourceResponse> responses =
                locationSourceQueryService.findDeviceStatusRecords(GUARDIAN_ID, WARD_ID, null, null);

        assertThat(responses).isEmpty();
    }

    @Test
    void unlinkedGuardianCannotFindDeviceStatusRecords() {
        User guardian = user(GUARDIAN_ID, UserRole.GUARDIAN);
        User ward = user(WARD_ID, UserRole.ELDER);
        when(userRepository.findById(GUARDIAN_ID)).thenReturn(Optional.of(guardian));
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
        when(guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                GUARDIAN_ID,
                WARD_ID,
                GuardianWardStatus.ACCEPTED
        )).thenReturn(false);

        assertThatThrownBy(() -> locationSourceQueryService.findDeviceStatusRecords(GUARDIAN_ID, WARD_ID, null, null))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.NOT_LINKED_TO_WARD);
        verifyNoInteractions(encryptedActivityLogRepository, commonCryptoService, mlKemKeyProvisionService);
    }

    private void stubElderAccess() {
        User elder = user(WARD_ID, UserRole.ELDER);
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(elder));
    }

    private void stubPrivateKey() {
        when(mlKemKeyProvisionService.readPrivateKey(WARD_ID)).thenReturn(WARD_PRIVATE_KEY);
    }

    private void stubDecrypt(EncryptedActivityLog log, DeviceStatusSourcePayload payload) {
        when(commonCryptoService.decryptActivityLogPayload(
                eq(log.getDataKeyId()),
                eq(log.getEncryptedPackage()),
                eq(log.getAadJson()),
                eq(WARD_ID),
                eq(CommonCryptoService.OWNER_TYPE_USER),
                any(byte[].class),
                eq(DeviceStatusSourcePayload.class)
        )).thenReturn(payload);
    }

    private DeviceStatusSourcePayload devicePayload(
            Long sourceId,
            DeviceState deviceState,
            LocalDateTime reportedAt,
            LocalDateTime disconnectedAt
    ) {
        return new DeviceStatusSourcePayload(
                WARD_ID,
                deviceState,
                LocalDateTime.of(2026, 5, 11, 21, 30),
                disconnectedAt,
                reportedAt,
                "device_status",
                sourceId,
                ActivityEventType.DEVICE_EVENT
        );
    }

    private EncryptedActivityLog encryptedLog(Long sourceId, LocalDateTime occurredAt) {
        return EncryptedActivityLog.builder()
                .wardId(WARD_ID)
                .dataKeyId("key-" + sourceId)
                .eventType(ActivityEventType.DEVICE_EVENT)
                .sourceTable("device_status")
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
