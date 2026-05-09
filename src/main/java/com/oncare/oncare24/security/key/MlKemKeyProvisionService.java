package com.oncare.oncare24.security.key;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.oncare.oncare24.security.crypto.ffi.JnaCryptoFfiClient;
import com.oncare.oncare24.security.crypto.ffi.MlKemKeyPair;
import com.oncare.oncare24.security.kms.OpenBaoKvClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MlKemKeyProvisionService {
    private final OpenBaoKvClient openBaoKvClient;

    @Value("${oncare.security.crypto.enabled:false}")
    private boolean cryptoEnabled;

    public void provisionUserMlKemKey(Long userId) {
        if (!cryptoEnabled) {
            log.info("Crypto provisioning skipped because disabled");
            return;
        }

        String path = userMlKemPath(userId);
        if (openBaoKvClient.exists(path)) {
            return;
        }

        MlKemKeyPair keyPair = new JnaCryptoFfiClient().generateMlKemKeypair();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("algorithm", keyPair.algorithm());
        data.put("public_key_b64", Base64.getEncoder().encodeToString(keyPair.publicKey()));
        data.put("private_key_b64", Base64.getEncoder().encodeToString(keyPair.privateKey()));
        data.put("created_at", Instant.now().toString());
        openBaoKvClient.put(path, data);
    }

    public byte[] readPublicKey(Long userId) {
        Map<String, Object> data = openBaoKvClient.get(userMlKemPath(userId));
        return decodeRequiredBase64(data, "public_key_b64");
    }

    public byte[] readPrivateKey(Long userId) {
        Map<String, Object> data = openBaoKvClient.get(userMlKemPath(userId));
        return decodeRequiredBase64(data, "private_key_b64");
    }

    private static String userMlKemPath(Long userId) {
        return "cap2/users/" + userId + "/mlkem";
    }

    private static byte[] decodeRequiredBase64(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("OpenBao secret missing field: " + fieldName);
        }
        return Base64.getDecoder().decode(text);
    }
}
