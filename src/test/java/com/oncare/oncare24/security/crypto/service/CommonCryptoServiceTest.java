package com.oncare.oncare24.security.crypto.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.security.crypto.dto.EncryptedPayload;
import com.oncare.oncare24.security.crypto.ffi.JnaCryptoFfiClient;
import com.oncare.oncare24.security.key.DataKeyProvisionService;

class CommonCryptoServiceTest {
    @Test
    void encryptSerializesPayloadAndReturnsEncryptedPackage() {
        DataKeyProvisionService dataKeyProvisionService = mock(DataKeyProvisionService.class);
        JnaCryptoFfiClient cryptoFfiClient = mock(JnaCryptoFfiClient.class);
        CommonCryptoService service = new CommonCryptoService(
                dataKeyProvisionService,
                new ObjectMapper(),
                cryptoFfiClient
        );
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);
        byte[] dataKey = new byte[32];
        byte[] encryptedPackage = new byte[] {9, 8, 7};
        when(dataKeyProvisionService.getOrCreateTodayDataKey())
                .thenReturn(new DataKeyProvisionService.ProvisionedDataKey("datakey-test", dataKey));
        when(cryptoFfiClient.encryptPackage(
                same(dataKey),
                eq("datakey-test"),
                any(byte[].class),
                eq(10L),
                any(byte[].class),
                eq(20L),
                any(byte[].class)
        )).thenReturn(encryptedPackage);

        EncryptedPayload payload = service.encryptForUserAndGuardian(
                Map.of("status", "ok"),
                Map.of("ward_id", 10L),
                10L,
                new byte[] {1},
                20L,
                new byte[] {2}
        );

        assertEquals("datakey-test", payload.dataKeyId());
        assertArrayEquals(encryptedPackage, payload.encryptedPackage());
        assertNotNull(payload.aadJson());
    }

    @Test
    void decryptLoadsDataKeyAndReturnsPlaintext() {
        DataKeyProvisionService dataKeyProvisionService = mock(DataKeyProvisionService.class);
        JnaCryptoFfiClient cryptoFfiClient = mock(JnaCryptoFfiClient.class);
        CommonCryptoService service = new CommonCryptoService(
                dataKeyProvisionService,
                new ObjectMapper(),
                cryptoFfiClient
        );
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);
        byte[] dataKey = new byte[32];
        byte[] encryptedPackage = new byte[] {1, 2, 3};
        byte[] plaintext = "{\"status\":\"ok\"}".getBytes();
        when(dataKeyProvisionService.getDataKey("datakey-test"))
                .thenReturn(new DataKeyProvisionService.ProvisionedDataKey("datakey-test", dataKey));
        when(cryptoFfiClient.decryptPackage(same(encryptedPackage), anyLong(), eq(CommonCryptoService.OWNER_TYPE_USER), any(byte[].class)))
                .thenReturn(plaintext);

        byte[] decrypted = service.decryptFromPackage(
                "datakey-test",
                encryptedPackage,
                "{\"ward_id\":10}",
                10L,
                CommonCryptoService.OWNER_TYPE_USER,
                new byte[] {4}
        );

        assertArrayEquals(plaintext, decrypted);
        verify(dataKeyProvisionService).getDataKey("datakey-test");
    }

    @Test
    void decryptActivityLogPayloadRejectsMetadataMismatch() {
        DataKeyProvisionService dataKeyProvisionService = mock(DataKeyProvisionService.class);
        JnaCryptoFfiClient cryptoFfiClient = mock(JnaCryptoFfiClient.class);
        CommonCryptoService service = new CommonCryptoService(
                dataKeyProvisionService,
                new ObjectMapper(),
                cryptoFfiClient
        );
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);
        byte[] encryptedPackage = new byte[] {1, 2, 3};
        byte[] plaintext = """
                {"metadata":{"ward_id":10,"event_type":"LOCATION"},"payload":{"status":"ok"}}
                """.getBytes();
        when(dataKeyProvisionService.getDataKey("datakey-test"))
                .thenReturn(new DataKeyProvisionService.ProvisionedDataKey("datakey-test", new byte[32]));
        when(cryptoFfiClient.decryptPackage(same(encryptedPackage), anyLong(), eq(CommonCryptoService.OWNER_TYPE_USER), any(byte[].class)))
                .thenReturn(plaintext);

        assertThrows(IllegalStateException.class, () -> service.decryptActivityLogPayload(
                "datakey-test",
                encryptedPackage,
                "{\"ward_id\":11,\"event_type\":\"LOCATION\"}",
                10L,
                CommonCryptoService.OWNER_TYPE_USER,
                new byte[] {4}
        ));
    }

    @Test
    void decryptActivityLogPayloadReturnsPayloadWhenMetadataMatches() {
        DataKeyProvisionService dataKeyProvisionService = mock(DataKeyProvisionService.class);
        JnaCryptoFfiClient cryptoFfiClient = mock(JnaCryptoFfiClient.class);
        CommonCryptoService service = new CommonCryptoService(
                dataKeyProvisionService,
                new ObjectMapper(),
                cryptoFfiClient
        );
        ReflectionTestUtils.setField(service, "cryptoEnabled", true);
        byte[] encryptedPackage = new byte[] {1, 2, 3};
        byte[] plaintext = """
                {"metadata":{"ward_id":10,"event_type":"LOCATION"},"payload":{"status":"ok"}}
                """.getBytes();
        when(dataKeyProvisionService.getDataKey("datakey-test"))
                .thenReturn(new DataKeyProvisionService.ProvisionedDataKey("datakey-test", new byte[32]));
        when(cryptoFfiClient.decryptPackage(same(encryptedPackage), anyLong(), eq(CommonCryptoService.OWNER_TYPE_USER), any(byte[].class)))
                .thenReturn(plaintext);

        Map<?, ?> payload = service.decryptActivityLogPayload(
                "datakey-test",
                encryptedPackage,
                "{\"ward_id\":10,\"event_type\":\"LOCATION\"}",
                10L,
                CommonCryptoService.OWNER_TYPE_USER,
                new byte[] {4},
                Map.class
        );

        assertEquals("ok", payload.get("status"));
    }
}
