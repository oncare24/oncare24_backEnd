package com.oncare.oncare24.security.crypto.ffi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CryptoFfiSmokeTest {
    @Test
    void generateDataKeyReturnsThirtyTwoBytes() {
        Path dllPath = CryptoFfiLoader.resolveLibraryPath();
        assertTrue(dllPath.toFile().isFile(), "crypto_ffi.dll must exist at resolved path: " + dllPath);

        byte[] dataKey = new JnaCryptoFfiClient().generateDataKey();

        assertNotNull(dataKey);
        assertEquals(32, dataKey.length);
    }

    @Test
    void generateMlKemKeypairReturnsPublicAndPrivateKeys() {
        Path dllPath = CryptoFfiLoader.resolveLibraryPath();
        assertTrue(dllPath.toFile().isFile(), "crypto_ffi.dll must exist at resolved path: " + dllPath);

        MlKemKeyPair keyPair = new JnaCryptoFfiClient().generateMlKemKeypair();

        assertEquals("ML-KEM-1024", keyPair.algorithm());
        assertNotNull(keyPair.publicKey());
        assertNotNull(keyPair.privateKey());
        assertTrue(keyPair.publicKey().length > 0);
        assertTrue(keyPair.privateKey().length > 0);
    }

    @Test
    void generatedMlKemKeypairOpensCreatedKeyEnvelope() {
        Path dllPath = CryptoFfiLoader.resolveLibraryPath();
        assertTrue(dllPath.toFile().isFile(), "crypto_ffi.dll must exist at resolved path: " + dllPath);

        JnaCryptoFfiClient client = new JnaCryptoFfiClient();
        byte[] dataKey = client.generateDataKey();
        MlKemKeyPair keyPair = client.generateMlKemKeypair();

        byte[] envelope = client.createKeyEnvelope(
                dataKey,
                "oncare24-backend-envelope-smoke-key",
                "101",
                CryptoFfiNative.FFI_OWNER_TYPE_USER,
                keyPair.publicKey()
        );
        byte[] openedDataKey = client.openKeyEnvelope(
                envelope,
                "101",
                CryptoFfiNative.FFI_OWNER_TYPE_USER,
                keyPair.privateKey()
        );

        assertArrayEquals(dataKey, openedDataKey);
    }
}
