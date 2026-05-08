package com.oncare.oncare24.security.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.oncare.oncare24.security.kms.OpenBaoKvClient;

class DataKeyProvisionServiceTest {
    @Test
    void getOrCreateTodayDataKeyCreatesSecretWhenMissing() {
        OpenBaoKvClient openBaoKvClient = mock(OpenBaoKvClient.class);
        DataKeyProvisionService service = new DataKeyProvisionService(openBaoKvClient);
        String keyId = todayKeyId();
        when(openBaoKvClient.exists("cap2/data-keys/" + keyId)).thenReturn(false);

        DataKeyProvisionService.ProvisionedDataKey dataKey = service.getOrCreateTodayDataKey();

        assertEquals(keyId, dataKey.keyId());
        assertEquals(32, dataKey.keyValue().length);
        verify(openBaoKvClient).put(eq("cap2/data-keys/" + keyId), anyMap());
    }

    @Test
    void getOrCreateTodayDataKeyReusesExistingSecret() {
        OpenBaoKvClient openBaoKvClient = mock(OpenBaoKvClient.class);
        DataKeyProvisionService service = new DataKeyProvisionService(openBaoKvClient);
        String keyId = todayKeyId();
        byte[] stored = new byte[32];
        stored[0] = 7;
        when(openBaoKvClient.exists("cap2/data-keys/" + keyId)).thenReturn(true);
        when(openBaoKvClient.get("cap2/data-keys/" + keyId))
                .thenReturn(Map.of("data_key_b64", Base64.getEncoder().encodeToString(stored)));

        DataKeyProvisionService.ProvisionedDataKey dataKey = service.getOrCreateTodayDataKey();

        assertEquals(keyId, dataKey.keyId());
        assertArrayEquals(stored, dataKey.keyValue());
    }

    private static String todayKeyId() {
        return "datakey-" + LocalDate.now(ZoneId.of("Asia/Seoul"));
    }
}
