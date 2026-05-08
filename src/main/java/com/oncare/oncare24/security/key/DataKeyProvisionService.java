package com.oncare.oncare24.security.key;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.oncare.oncare24.security.crypto.ffi.JnaCryptoFfiClient;
import com.oncare.oncare24.security.kms.OpenBaoKvClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataKeyProvisionService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final OpenBaoKvClient openBaoKvClient;

    public ProvisionedDataKey getOrCreateTodayDataKey() {
        String keyId = todayKeyId();
        String path = dataKeyPath(keyId);
        if (openBaoKvClient.exists(path)) {
            Map<String, Object> data = openBaoKvClient.get(path);
            return new ProvisionedDataKey(keyId, decodeRequiredBase64(data, "data_key_b64"));
        }

        byte[] dataKey = new JnaCryptoFfiClient().generateDataKey();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key_id", keyId);
        data.put("data_key_b64", Base64.getEncoder().encodeToString(dataKey));
        data.put("created_at", Instant.now().toString());
        openBaoKvClient.put(path, data);
        return new ProvisionedDataKey(keyId, dataKey);
    }

    private static String todayKeyId() {
        return "datakey-" + LocalDate.now(SEOUL);
    }

    private static String dataKeyPath(String keyId) {
        return "cap2/data-keys/" + keyId;
    }

    private static byte[] decodeRequiredBase64(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("OpenBao secret missing field: " + fieldName);
        }
        return Base64.getDecoder().decode(text);
    }

    public record ProvisionedDataKey(String keyId, byte[] keyValue) {
    }
}
