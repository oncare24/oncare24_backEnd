package com.oncare.oncare24.security.crypto.ffi;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MlKemKeyPair(
        String algorithm,
        @JsonProperty("public_key") byte[] publicKey,
        @JsonProperty("private_key") byte[] privateKey
) {
}
