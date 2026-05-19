package com.oncare.oncare24.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.security.crypto.dto.EncryptedPayload;
import com.oncare.oncare24.security.crypto.service.CommonCryptoService;
import com.oncare.oncare24.security.envelope.KeyEnvelopeProvisionService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;

class EncryptedSourceEventServiceTest {
    @Test
    void saveSourceEventEncryptsSourcePayloadAndStoresEncryptedPackage() {
        GuardianWardRepository guardianWardRepository = mock(GuardianWardRepository.class);
        MlKemKeyProvisionService mlKemKeyProvisionService = mock(MlKemKeyProvisionService.class);
        CommonCryptoService commonCryptoService = mock(CommonCryptoService.class);
        EncryptedActivityLogService encryptedActivityLogService = mock(EncryptedActivityLogService.class);
        KeyEnvelopeProvisionService keyEnvelopeProvisionService = mock(KeyEnvelopeProvisionService.class);
        EncryptedSourceEventService service = new EncryptedSourceEventService(
                guardianWardRepository,
                mlKemKeyProvisionService,
                commonCryptoService,
                encryptedActivityLogService,
                keyEnvelopeProvisionService
        );
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);

        GuardianWard guardianWard = GuardianWard.builder()
                .wardId(1L)
                .guardianId(20L)
                .inviteCode("123456")
                .build();
        guardianWard.accept();
        Map<String, Object> sourcePayload = Map.of(
                "schedule_id", 10L,
                "taken_at", "2026-05-09T08:00:00",
                "allowed_delay_min", 30
        );
        EncryptedPayload encryptedPayload = new EncryptedPayload(
                "datakey-test",
                new byte[] {1, 2, 3},
                "{\"ward_id\":1}"
        );

        when(guardianWardRepository.findByWardIdAndStatus(1L, GuardianWardStatus.ACCEPTED))
                .thenReturn(List.of(guardianWard));
        when(mlKemKeyProvisionService.readPublicKey(1L)).thenReturn(new byte[] {1});
        when(mlKemKeyProvisionService.readPublicKey(20L)).thenReturn(new byte[] {2});
        when(commonCryptoService.encryptForUserAndGuardian(
                any(),
                eq(1L),
                eq(ActivityEventType.MEDICATION_EVENT.name()),
                any(LocalDateTime.class),
                eq("medication_log"),
                eq(100L),
                eq(1L),
                any(byte[].class),
                eq(20L),
                any(byte[].class)
        )).thenReturn(encryptedPayload);

        service.saveSourceEvent(
                1L,
                ActivityEventType.MEDICATION_EVENT,
                "medication_log",
                100L,
                LocalDateTime.of(2026, 5, 9, 8, 0),
                sourcePayload
        );

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(commonCryptoService).encryptForUserAndGuardian(
                payloadCaptor.capture(),
                eq(1L),
                eq(ActivityEventType.MEDICATION_EVENT.name()),
                any(LocalDateTime.class),
                eq("medication_log"),
                eq(100L),
                eq(1L),
                any(byte[].class),
                eq(20L),
                any(byte[].class)
        );
        Map<String, Object> capturedPayload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(capturedPayload).containsEntry("schedule_id", 10L);
        verify(encryptedActivityLogService).saveEncryptedActivityLog(
                eq(1L),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_log"),
                eq(100L),
                any(LocalDateTime.class),
                eq(encryptedPayload)
        );
        verify(keyEnvelopeProvisionService).provisionUserEnvelopeForDataKeyId(1L, "datakey-test");
        verify(keyEnvelopeProvisionService).provisionGuardianEnvelopeForDataKeyId(1L, 20L, "datakey-test");
    }

    @Test
    void saveRequiredSourceEventThrowsWhenCryptoDisabledInsteadOfSkippingMedicationEvent() {
        GuardianWardRepository guardianWardRepository = mock(GuardianWardRepository.class);
        MlKemKeyProvisionService mlKemKeyProvisionService = mock(MlKemKeyProvisionService.class);
        CommonCryptoService commonCryptoService = mock(CommonCryptoService.class);
        EncryptedActivityLogService encryptedActivityLogService = mock(EncryptedActivityLogService.class);
        KeyEnvelopeProvisionService keyEnvelopeProvisionService = mock(KeyEnvelopeProvisionService.class);
        EncryptedSourceEventService service = new EncryptedSourceEventService(
                guardianWardRepository,
                mlKemKeyProvisionService,
                commonCryptoService,
                encryptedActivityLogService,
                keyEnvelopeProvisionService
        );
        ReflectionTestUtils.setField(service, "cryptoEnabled", false);

        assertThatThrownBy(() -> service.saveRequiredSourceEvent(
                1L,
                ActivityEventType.MEDICATION_EVENT,
                "medication_log",
                100L,
                LocalDateTime.of(2026, 5, 9, 8, 0),
                Map.of("taken_at", "2026-05-09T08:00:00")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("crypto must be enabled");

        verifyNoInteractions(guardianWardRepository, mlKemKeyProvisionService, commonCryptoService, encryptedActivityLogService, keyEnvelopeProvisionService);
    }

    @Test
    void saveRequiredSourceEventStoresWardOnlyEncryptedEventWhenNoAcceptedGuardian() {
        GuardianWardRepository guardianWardRepository = mock(GuardianWardRepository.class);
        MlKemKeyProvisionService mlKemKeyProvisionService = mock(MlKemKeyProvisionService.class);
        CommonCryptoService commonCryptoService = mock(CommonCryptoService.class);
        EncryptedActivityLogService encryptedActivityLogService = mock(EncryptedActivityLogService.class);
        KeyEnvelopeProvisionService keyEnvelopeProvisionService = mock(KeyEnvelopeProvisionService.class);
        EncryptedSourceEventService service = new EncryptedSourceEventService(
                guardianWardRepository,
                mlKemKeyProvisionService,
                commonCryptoService,
                encryptedActivityLogService,
                keyEnvelopeProvisionService
        );
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);

        EncryptedPayload encryptedPayload = new EncryptedPayload(
                "datakey-ward-only",
                new byte[] {1, 2, 3},
                "{\"ward_id\":1}"
        );
        when(guardianWardRepository.findByWardIdAndStatus(1L, GuardianWardStatus.ACCEPTED))
                .thenReturn(List.of());
        when(mlKemKeyProvisionService.readPublicKey(1L)).thenReturn(new byte[] {1});
        when(commonCryptoService.encryptForUser(
                any(),
                eq(1L),
                eq(ActivityEventType.MEDICATION_EVENT.name()),
                any(LocalDateTime.class),
                eq("medication_schedule"),
                eq(10L),
                eq(1L),
                any(byte[].class)
        )).thenReturn(encryptedPayload);

        service.saveRequiredSourceEvent(
                1L,
                ActivityEventType.MEDICATION_EVENT,
                "medication_schedule",
                10L,
                LocalDateTime.of(2026, 5, 9, 8, 0),
                Map.of("medication_name", "morning pill")
        );

        verify(encryptedActivityLogService).saveEncryptedActivityLog(
                eq(1L),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                eq(10L),
                any(LocalDateTime.class),
                eq(encryptedPayload)
        );
        verify(keyEnvelopeProvisionService).provisionUserEnvelopeForDataKeyId(1L, "datakey-ward-only");
        verify(keyEnvelopeProvisionService, never()).provisionGuardianEnvelopeForDataKeyId(any(), any(), any());
    }
}
