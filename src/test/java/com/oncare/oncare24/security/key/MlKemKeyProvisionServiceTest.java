package com.oncare.oncare24.security.key;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.oncare.oncare24.security.kms.OpenBaoKvClient;

class MlKemKeyProvisionServiceTest {
    @Test
    void provisionUserMlKemKeyCreatesSecretWhenMissing() {
        OpenBaoKvClient openBaoKvClient = mock(OpenBaoKvClient.class);
        MlKemKeyProvisionService service = new MlKemKeyProvisionService(openBaoKvClient);
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);
        when(openBaoKvClient.exists("cap2/users/10/mlkem")).thenReturn(false);

        service.provisionUserMlKemKey(10L);

        verify(openBaoKvClient).put(eq("cap2/users/10/mlkem"), anyMap());
    }

    @Test
    void provisionUserMlKemKeySkipsExistingSecret() {
        OpenBaoKvClient openBaoKvClient = mock(OpenBaoKvClient.class);
        MlKemKeyProvisionService service = new MlKemKeyProvisionService(openBaoKvClient);
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);
        when(openBaoKvClient.exists("cap2/users/10/mlkem")).thenReturn(true);

        service.provisionUserMlKemKey(10L);

        verify(openBaoKvClient, never()).put(eq("cap2/users/10/mlkem"), anyMap());
    }
}
