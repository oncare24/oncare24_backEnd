package com.oncare.oncare24.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.security.crypto.dto.EncryptedPayload;

class EncryptedActivityLogServiceTest {
    @Test
    void saveEncryptedActivityLogStoresEncryptedPackage() {
        EncryptedActivityLogRepository repository = mock(EncryptedActivityLogRepository.class);
        EncryptedActivityLogService service = new EncryptedActivityLogService(repository);
        EncryptedPayload payload = new EncryptedPayload(
                "datakey-test",
                new byte[] {1, 2, 3},
                "{\"ward_id\":1}"
        );

        service.saveEncryptedActivityLog(
                1L,
                ActivityEventType.MEDICATION_EVENT,
                "medication_schedule",
                10L,
                LocalDateTime.of(2026, 5, 9, 8, 0),
                payload
        );

        ArgumentCaptor<EncryptedActivityLog> captor = ArgumentCaptor.forClass(EncryptedActivityLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedPackage()).containsExactly(1, 2, 3);
        assertThat(captor.getValue().getDataKeyId()).isEqualTo("datakey-test");
    }
}
