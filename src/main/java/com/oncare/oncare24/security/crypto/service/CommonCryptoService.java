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

    // 사용자와 보호자 공개키 기반 payload 암호화
    public EncryptedPayload encryptForUserAndGuardian(
            Object plaintextPayload,
            Map<String, Object> aad,
            long userId,
            byte[] userPublicKey,
            long guardianId,
            byte[] guardianPublicKey
    ) {
        requireCryptoEnabled();
        // OpenBao/KMS에서 오늘 data key 조회 또는 생성
        DataKeyProvisionService.ProvisionedDataKey dataKey = dataKeyProvisionService.getOrCreateTodayDataKey();
        // 원천 payload를 JSON 바이트 평문으로 직렬화
        byte[] plaintext = writeJsonBytes(plaintextPayload);
        // 암호화 메타데이터를 AAD JSON으로 직렬화
        String aadJson = writeAadJson(aad);
        // Rust FFI로 data key와 수신자 공개키를 전달해 암호화 패키지 생성
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

    // 사용자 공개키 기반 payload 암호화
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

    // 암호화 패키지 원문 복호화
    public byte[] decryptFromPackage(
            String dataKeyId,
            byte[] encryptedPackage,
            String aadJson,
            long callerId,
            int callerType,
            byte[] privateKey
    ) {
        requireCryptoEnabled();
        // data key 존재 여부를 OpenBao/KMS에서 확인
        dataKeyProvisionService.getDataKey(dataKeyId);
        // 저장된 AAD JSON 형식 검증
        requireValidAadJson(aadJson);
        // Rust FFI로 암호화 패키지를 복호화
        return cryptoFfiClient.decryptPackage(encryptedPackage, callerId, callerType, privateKey);
    }

    // 암호화 패키지 원문 복호화
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

    // 사용자와 보호자 공개키 기반 payload 암호화
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

    // 사용자 공개키 기반 payload 암호화
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

    // 사용자와 보호자 공개키 기반 payload 암호화
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

    // encrypted_activity_log payload 복호화
    public byte[] decryptActivityLogPayload(
            String dataKeyId,
            byte[] encryptedPackage,
            String aadJson,
            long callerId,
            int callerType,
            byte[] privateKey
    ) {
        // encrypted_activity_log 전체 평문을 복호화
        byte[] plaintext = decryptFromPackage(dataKeyId, encryptedPackage, aadJson, callerId, callerType, privateKey);
        JsonNode root = readPlaintextTree(plaintext);
        // 복호화된 metadata와 저장된 AAD 일치 여부 검증
        verifyPlaintextMetadataMatchesAad(root, aadJson);
        JsonNode payload = root.get("payload");
        if (payload == null) {
            throw new IllegalStateException("decrypted activity log payload missing payload");
        }
        return writeJsonBytes(payload);
    }

    // encrypted_activity_log payload 복호화
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

    // 활동 로그 AAD 메타데이터 생성
    public static Map<String, Object> activityLogAad(
            long wardId,
            String eventType,
            String sourceTable,
            Long sourceId
    ) {
        return activityLogMetadata(wardId, eventType, null, sourceTable, sourceId);
    }

    // 활동 로그 암호화 메타데이터 생성
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

    // 암호화 대상 payload JSON 바이트 변환
    private byte[] writeJsonBytes(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize payload for encryption", error);
        }
    }

    // AAD 메타데이터 JSON 변환
    private String writeAadJson(Map<String, Object> aad) {
        try {
            return objectMapper.writeValueAsString(aad == null ? Map.of() : aad);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize AAD JSON", error);
        }
    }

    // 저장된 AAD JSON 유효성 확인
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

    // 복호화된 JSON 평문 파싱
    private JsonNode readPlaintextTree(byte[] plaintext) {
        try {
            return objectMapper.readTree(plaintext);
        } catch (IOException error) {
            throw new IllegalStateException("decrypted activity log payload must be JSON", error);
        }
    }

    // 복호화 metadata와 AAD 일치 검증
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

    // AAD JSON 트리 파싱
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

    // 암호화 기능 활성화 여부 확인
    private void requireCryptoEnabled() {
        if (!cryptoEnabled) {
            throw new IllegalStateException("Crypto is disabled. Set ONCARE_SECURITY_CRYPTO_ENABLED=true to use CommonCryptoService.");
        }
    }
}
