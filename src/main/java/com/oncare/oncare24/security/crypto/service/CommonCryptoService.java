package com.oncare.oncare24.security.crypto.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.security.crypto.dto.EncryptedPayload;
import com.oncare.oncare24.security.crypto.ffi.JnaCryptoFfiClient;
import com.oncare.oncare24.security.key.DataKeyProvisionService;

@Service
public class CommonCryptoService {
    public static final int OWNER_TYPE_USER = 1;
    public static final int OWNER_TYPE_GUARDIAN = 2;

    private final DataKeyProvisionService dataKeyProvisionService;
    private final ObjectMapper objectMapper;
    private final JnaCryptoFfiClient cryptoFfiClient;

    @Value("${oncare.security.crypto.enabled:false}")
    private boolean cryptoEnabled;

    @Autowired
    public CommonCryptoService(DataKeyProvisionService dataKeyProvisionService, ObjectMapper objectMapper) {
        this(dataKeyProvisionService, objectMapper, new JnaCryptoFfiClient());
    }

    CommonCryptoService(
            DataKeyProvisionService dataKeyProvisionService,
            ObjectMapper objectMapper,
            JnaCryptoFfiClient cryptoFfiClient
    ) {
        this.dataKeyProvisionService = dataKeyProvisionService;
        this.objectMapper = objectMapper;
        this.cryptoFfiClient = cryptoFfiClient;
    }

    public EncryptedPayload encryptForUserAndGuardian(
            Object plaintextPayload,
            Map<String, Object> aad,
            long userId,
            byte[] userPublicKey,
            long guardianId,
            byte[] guardianPublicKey
    ) {
        requireCryptoEnabled();
        DataKeyProvisionService.ProvisionedDataKey dataKey = dataKeyProvisionService.getOrCreateTodayDataKey();
        byte[] plaintext = writeJsonBytes(plaintextPayload);
        String aadJson = writeAadJson(aad);
        byte[] encryptedPackage = cryptoFfiClient.encryptPackage(
                dataKey.keyValue(),
                dataKey.keyId(),
                plaintext,
                userId,
                userPublicKey,
                guardianId,
                guardianPublicKey
        );
        return new EncryptedPayload(dataKey.keyId(), encryptedPackage, aadJson);
    }

    public EncryptedPayload encryptForUser(
            Object plaintextPayload,
            Map<String, Object> aad,
            long userId,
            byte[] userPublicKey
    ) {
        return encryptForUserAndGuardian(
                plaintextPayload,
                aad,
                userId,
                userPublicKey,
                userId,
                userPublicKey
        );
    }

    public byte[] decryptFromPackage(
            String dataKeyId,
            byte[] encryptedPackage,
            String aadJson,
            long callerId,
            int callerType,
            byte[] privateKey
    ) {
        requireCryptoEnabled();
        dataKeyProvisionService.getDataKey(dataKeyId);
        requireValidAadJson(aadJson);
        return cryptoFfiClient.decryptPackage(encryptedPackage, callerId, callerType, privateKey);
    }

    public <T> T decryptFromPackage(
            String dataKeyId,
            byte[] encryptedPackage,
            String aadJson,
            long callerId,
            int callerType,
            byte[] privateKey,
            Class<T> valueType
    ) {
        byte[] plaintext = decryptFromPackage(dataKeyId, encryptedPackage, aadJson, callerId, callerType, privateKey);
        try {
            return objectMapper.readValue(plaintext, valueType);
        } catch (IOException error) {
            throw new IllegalStateException("failed to deserialize decrypted payload", error);
        }
    }

    public EncryptedPayload encryptForUserAndGuardian(
            Object plaintextPayload,
            long wardId,
            String eventType,
            LocalDateTime occurredAt,
            String sourceTable,
            Long sourceId,
            long userId,
            byte[] userPublicKey,
            long guardianId,
            byte[] guardianPublicKey
    ) {
        Map<String, Object> aad = activityLogMetadata(wardId, eventType, occurredAt, sourceTable, sourceId);
        Map<String, Object> plaintext = new LinkedHashMap<>();
        plaintext.put("metadata", aad);
        plaintext.put("payload", plaintextPayload);
        return encryptForUserAndGuardian(
                plaintext,
                aad,
                userId,
                userPublicKey,
                guardianId,
                guardianPublicKey
        );
    }

    public EncryptedPayload encryptForUser(
            Object plaintextPayload,
            long wardId,
            String eventType,
            LocalDateTime occurredAt,
            String sourceTable,
            Long sourceId,
            long userId,
            byte[] userPublicKey
    ) {
        Map<String, Object> aad = activityLogMetadata(wardId, eventType, occurredAt, sourceTable, sourceId);
        Map<String, Object> plaintext = new LinkedHashMap<>();
        plaintext.put("metadata", aad);
        plaintext.put("payload", plaintextPayload);
        return encryptForUser(plaintext, aad, userId, userPublicKey);
    }

    public EncryptedPayload encryptForUserAndGuardian(
            Object plaintextPayload,
            long wardId,
            String eventType,
            String sourceTable,
            Long sourceId,
            long userId,
            byte[] userPublicKey,
            long guardianId,
            byte[] guardianPublicKey
    ) {
        return encryptForUserAndGuardian(
                plaintextPayload,
                wardId,
                eventType,
                null,
                sourceTable,
                sourceId,
                userId,
                userPublicKey,
                guardianId,
                guardianPublicKey
        );
    }

    public byte[] decryptActivityLogPayload(
            String dataKeyId,
            byte[] encryptedPackage,
            String aadJson,
            long callerId,
            int callerType,
            byte[] privateKey
    ) {
        byte[] plaintext = decryptFromPackage(dataKeyId, encryptedPackage, aadJson, callerId, callerType, privateKey);
        JsonNode root = readPlaintextTree(plaintext);
        verifyPlaintextMetadataMatchesAad(root, aadJson);
        JsonNode payload = root.get("payload");
        if (payload == null) {
            throw new IllegalStateException("decrypted activity log payload missing payload");
        }
        return writeJsonBytes(payload);
    }

    public <T> T decryptActivityLogPayload(
            String dataKeyId,
            byte[] encryptedPackage,
            String aadJson,
            long callerId,
            int callerType,
            byte[] privateKey,
            Class<T> valueType
    ) {
        byte[] payload = decryptActivityLogPayload(dataKeyId, encryptedPackage, aadJson, callerId, callerType, privateKey);
        try {
            return objectMapper.readValue(payload, valueType);
        } catch (IOException error) {
            throw new IllegalStateException("failed to deserialize decrypted activity log payload", error);
        }
    }

    public static Map<String, Object> activityLogAad(
            long wardId,
            String eventType,
            String sourceTable,
            Long sourceId
    ) {
        return activityLogMetadata(wardId, eventType, null, sourceTable, sourceId);
    }

    public static Map<String, Object> activityLogMetadata(
            long wardId,
            String eventType,
            LocalDateTime occurredAt,
            String sourceTable,
            Long sourceId
    ) {
        Map<String, Object> aad = new LinkedHashMap<>();
        aad.put("ward_id", wardId);
        aad.put("event_type", eventType);
        if (occurredAt != null) {
            aad.put("occurred_at", occurredAt);
        }
        if (sourceTable != null && !sourceTable.isBlank()) {
            aad.put("source_table", sourceTable);
        }
        if (sourceId != null) {
            aad.put("source_id", sourceId);
        }
        return aad;
    }

    public static int ownerTypeUser() {
        return OWNER_TYPE_USER;
    }

    public static int ownerTypeGuardian() {
        return OWNER_TYPE_GUARDIAN;
    }

    private byte[] writeJsonBytes(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize payload for encryption", error);
        }
    }

    private String writeAadJson(Map<String, Object> aad) {
        try {
            return objectMapper.writeValueAsString(aad == null ? Map.of() : aad);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize AAD JSON", error);
        }
    }

    private void requireValidAadJson(String aadJson) {
        if (aadJson == null || aadJson.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(aadJson.getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalArgumentException("aadJson must be valid JSON", error);
        }
    }

    private JsonNode readPlaintextTree(byte[] plaintext) {
        try {
            return objectMapper.readTree(plaintext);
        } catch (IOException error) {
            throw new IllegalStateException("decrypted activity log payload must be JSON", error);
        }
    }

    private void verifyPlaintextMetadataMatchesAad(JsonNode plaintextRoot, String aadJson) {
        JsonNode metadata = plaintextRoot.get("metadata");
        if (metadata == null || !metadata.isObject()) {
            throw new IllegalStateException("decrypted activity log payload missing metadata object");
        }
        JsonNode aad = readAadTree(aadJson);
        if (!metadata.equals(aad)) {
            throw new IllegalStateException("decrypted activity log metadata does not match stored aadJson");
        }
    }

    private JsonNode readAadTree(String aadJson) {
        if (aadJson == null || aadJson.isBlank()) {
            throw new IllegalStateException("aadJson is required for activity log metadata verification");
        }
        try {
            return objectMapper.readTree(aadJson.getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalArgumentException("aadJson must be valid JSON", error);
        }
    }

    private void requireCryptoEnabled() {
        if (!cryptoEnabled) {
            throw new IllegalStateException("Crypto is disabled. Set ONCARE_SECURITY_CRYPTO_ENABLED=true to use CommonCryptoService.");
        }
    }
}
