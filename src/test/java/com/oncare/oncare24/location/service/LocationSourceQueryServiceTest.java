package com.oncare.oncare24.location.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.location.dto.LocationSourcePayload;
import com.oncare.oncare24.location.dto.LocationSourceResponse;
import com.oncare.oncare24.location.entity.LocationReportSource;
import com.oncare.oncare24.security.crypto.service.CommonCryptoService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationSourceQueryServiceTest {

    private static final Long WARD_ID = 1L;
    private static final byte[] WARD_PRIVATE_KEY = new byte[]{1, 2, 3};

    private EncryptedActivityLogRepository encryptedActivityLogRepository;
    private CommonCryptoService commonCryptoService;
    private MlKemKeyProvisionService mlKemKeyProvisionService;
    private UserRepository userRepository;
    private LocationSourceQueryService locationSourceQueryService;

    @BeforeEach
    void setUp() {
        encryptedActivityLogRepository = mock(EncryptedActivityLogRepository.class);
        commonCryptoService = mock(CommonCryptoService.class);
        mlKemKeyProvisionService = mock(MlKemKeyProvisionService.class);
        userRepository = mock(UserRepository.class);
        GuardianWardRepository guardianWardRepository = mock(GuardianWardRepository.class);
        locationSourceQueryService = new LocationSourceQueryService(
                encryptedActivityLogRepository,
                commonCryptoService,
                mlKemKeyProvisionService,
                userRepository,
                guardianWardRepository
        );
    }

    @Test
    void findLocationRecordsDecryptsLocationEventsAndSortsByReportedAt() {
        stubElderAccess();
        stubPrivateKey();
        LocalDateTime from = LocalDateTime.of(2026, 5, 11, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 5, 12, 0, 0);
        EncryptedActivityLog later = encryptedLog(ActivityEventType.LOCATION_EVENT, 101L, LocalDateTime.of(2026, 5, 11, 22, 0));
        EncryptedActivityLog earlier = encryptedLog(ActivityEventType.LOCATION_EVENT, 100L, LocalDateTime.of(2026, 5, 11, 21, 0));
        when(encryptedActivityLogRepository.findByWardIdAndEventTypeAndOccurredAtBetweenOrderByOccurredAtDesc(
                WARD_ID,
                ActivityEventType.LOCATION_EVENT,
                from,
                to
        )).thenReturn(List.of(later, earlier));
        stubDecrypt(later, locationPayload(101L, LocalDateTime.of(2026, 5, 11, 22, 0), "37.6", "127.1"));
        stubDecrypt(earlier, locationPayload(100L, LocalDateTime.of(2026, 5, 11, 21, 0), "37.5", "127.0"));

        List<LocationSourceResponse> responses =
                locationSourceQueryService.findLocationRecords(WARD_ID, WARD_ID, from, to);

        assertThat(responses).extracting(LocationSourceResponse::reportedAt)
                .containsExactly(
                        LocalDateTime.of(2026, 5, 11, 21, 0),
                        LocalDateTime.of(2026, 5, 11, 22, 0)
                );
        assertThat(responses.get(0).latitude()).isEqualByComparingTo("37.5");
        assertThat(responses.get(0).reportSource()).isEqualTo(LocationReportSource.BACKGROUND_SCHEDULED);
        verify(encryptedActivityLogRepository).findByWardIdAndEventTypeAndOccurredAtBetweenOrderByOccurredAtDesc(
                WARD_ID,
                ActivityEventType.LOCATION_EVENT,
                from,
                to
        );
    }

    private void stubElderAccess() {
        User elder = user(WARD_ID, UserRole.ELDER);
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(elder));
    }

    private void stubPrivateKey() {
        when(mlKemKeyProvisionService.readPrivateKey(WARD_ID)).thenReturn(WARD_PRIVATE_KEY);
    }

    private void stubDecrypt(EncryptedActivityLog log, LocationSourcePayload payload) {
        when(commonCryptoService.decryptActivityLogPayload(
                eq(log.getDataKeyId()),
                eq(log.getEncryptedPackage()),
                eq(log.getAadJson()),
                eq(WARD_ID),
                eq(CommonCryptoService.OWNER_TYPE_USER),
                any(byte[].class),
                eq(LocationSourcePayload.class)
        )).thenReturn(payload);
    }

    private LocationSourcePayload locationPayload(Long sourceId, LocalDateTime reportedAt, String latitude, String longitude) {
        return new LocationSourcePayload(
                WARD_ID,
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                20.0,
                reportedAt,
                "location_report",
                sourceId,
                ActivityEventType.LOCATION_EVENT,
                LocationReportSource.BACKGROUND_SCHEDULED
        );
    }

    private EncryptedActivityLog encryptedLog(ActivityEventType eventType, Long sourceId, LocalDateTime occurredAt) {
        return EncryptedActivityLog.builder()
                .wardId(WARD_ID)
                .dataKeyId("key-" + sourceId)
                .eventType(eventType)
                .sourceTable("location_report")
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
