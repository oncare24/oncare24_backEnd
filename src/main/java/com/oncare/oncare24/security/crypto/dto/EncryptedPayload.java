package com.oncare.oncare24.security.crypto.dto;

public record EncryptedPayload(
        String dataKeyId,
        byte[] encryptedPackage,
        String aadJson
) {
}
