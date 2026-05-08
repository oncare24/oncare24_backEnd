package com.oncare.oncare24.security.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.oncare.oncare24.security.crypto.ffi.JnaCryptoFfiClient;
import com.oncare.oncare24.security.crypto.ffi.MlKemKeyPair;
import com.oncare.oncare24.security.key.DataKeyProvisionService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.security.kms.OpenBaoKvClient;

class KeyEnvelopeProvisionServiceTest {
    @Test
    void provisionForAcceptedGuardianCreatesEnvelopeWhenMissing() {
        OpenBaoKvClient openBaoKvClient = mock(OpenBaoKvClient.class);
        DataKeyProvisionService dataKeyProvisionService = mock(DataKeyProvisionService.class);
        MlKemKeyProvisionService mlKemKeyProvisionService = mock(MlKemKeyProvisionService.class);
        KeyEnvelopeProvisionService service = new KeyEnvelopeProvisionService(
                openBaoKvClient,
                dataKeyProvisionService,
                mlKemKeyProvisionService
        );
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);

        byte[] dataKey = new JnaCryptoFfiClient().generateDataKey();
        MlKemKeyPair keyPair = new JnaCryptoFfiClient().generateMlKemKeypair();
        when(dataKeyProvisionService.getOrCreateTodayDataKey())
                .thenReturn(new DataKeyProvisionService.ProvisionedDataKey("datakey-test", dataKey));
        when(openBaoKvClient.exists("cap2/key-envelopes/datakey-test/guardian-20")).thenReturn(false);
        when(mlKemKeyProvisionService.readPublicKey(20L)).thenReturn(keyPair.publicKey());

        service.provisionForAcceptedGuardian(10L, 20L);

        verify(mlKemKeyProvisionService).provisionUserMlKemKey(20L);
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(openBaoKvClient).put(eq("cap2/key-envelopes/datakey-test/guardian-20"), dataCaptor.capture());
        assertEquals(10L, dataCaptor.getValue().get("ward_id"));
    }

    @Test
    void provisionForAcceptedGuardianSkipsExistingEnvelope() {
        OpenBaoKvClient openBaoKvClient = mock(OpenBaoKvClient.class);
        DataKeyProvisionService dataKeyProvisionService = mock(DataKeyProvisionService.class);
        MlKemKeyProvisionService mlKemKeyProvisionService = mock(MlKemKeyProvisionService.class);
        KeyEnvelopeProvisionService service = new KeyEnvelopeProvisionService(
                openBaoKvClient,
                dataKeyProvisionService,
                mlKemKeyProvisionService
        );
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);
        when(dataKeyProvisionService.getOrCreateTodayDataKey())
                .thenReturn(new DataKeyProvisionService.ProvisionedDataKey("datakey-test", new byte[32]));
        when(openBaoKvClient.exists("cap2/key-envelopes/datakey-test/guardian-20")).thenReturn(true);

        service.provisionForAcceptedGuardian(10L, 20L);

        verify(mlKemKeyProvisionService, never()).readPublicKey(20L);
        verify(openBaoKvClient, never()).put(eq("cap2/key-envelopes/datakey-test/guardian-20"), anyMap());
    }
}
