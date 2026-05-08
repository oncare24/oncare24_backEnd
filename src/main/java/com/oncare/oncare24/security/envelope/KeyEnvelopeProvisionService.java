package com.oncare.oncare24.security.envelope;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.oncare.oncare24.security.crypto.ffi.JnaCryptoFfiClient;
import com.oncare.oncare24.security.key.DataKeyProvisionService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.security.kms.OpenBaoKvClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyEnvelopeProvisionService {
    private static final int FFI_OWNER_TYPE_USER = 1;
    private static final int FFI_OWNER_TYPE_GUARDIAN = 2;

    private final OpenBaoKvClient openBaoKvClient;
    private final DataKeyProvisionService dataKeyProvisionService;
    private final MlKemKeyProvisionService mlKemKeyProvisionService;

    @Value("${oncare.security.crypto.enabled:false}")
    private boolean cryptoEnabled;

    public void provisionForAcceptedGuardian(Long wardId, Long guardianId) {
        if (!cryptoEnabled) {
            log.info("Crypto provisioning skipped because disabled");
            return;
        }

        DataKeyProvisionService.ProvisionedDataKey dataKey = dataKeyProvisionService.getOrCreateTodayDataKey();
        String path = guardianEnvelopePath(dataKey.keyId(), guardianId);
        if (openBaoKvClient.exists(path)) {
            return;
        }

        mlKemKeyProvisionService.provisionUserMlKemKey(guardianId);
        byte[] guardianPublicKey = mlKemKeyProvisionService.readPublicKey(guardianId);
        byte[] envelopeJson = new JnaCryptoFfiClient().createKeyEnvelope(
                dataKey.keyValue(),
                dataKey.keyId(),
                guardianId.toString(),
                FFI_OWNER_TYPE_GUARDIAN,
                guardianPublicKey
        );
        storeEnvelope(path, dataKey.keyId(), wardId, guardianId, "GUARDIAN", envelopeJson);
    }

    public void provisionUserEnvelope(Long userId) {
        if (!cryptoEnabled) {
            log.info("Crypto provisioning skipped because disabled");
            return;
        }

        DataKeyProvisionService.ProvisionedDataKey dataKey = dataKeyProvisionService.getOrCreateTodayDataKey();
        String path = userEnvelopePath(dataKey.keyId(), userId);
        if (openBaoKvClient.exists(path)) {
            return;
        }

        mlKemKeyProvisionService.provisionUserMlKemKey(userId);
        byte[] userPublicKey = mlKemKeyProvisionService.readPublicKey(userId);
        byte[] envelopeJson = new JnaCryptoFfiClient().createKeyEnvelope(
                dataKey.keyValue(),
                dataKey.keyId(),
                userId.toString(),
                FFI_OWNER_TYPE_USER,
                userPublicKey
        );
        storeEnvelope(path, dataKey.keyId(), null, userId, "USER", envelopeJson);
    }

    private void storeEnvelope(String path, String keyId, Long wardId, Long ownerId, String ownerType, byte[] envelopeJson) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key_id", keyId);
        if (wardId != null) {
            data.put("ward_id", wardId);
        }
        data.put("owner_id", ownerId);
        data.put("owner_type", ownerType);
        data.put("envelope_b64", Base64.getEncoder().encodeToString(envelopeJson));
        data.put("created_at", Instant.now().toString());
        openBaoKvClient.put(path, data);
    }

    private static String guardianEnvelopePath(String keyId, Long guardianId) {
        return "cap2/key-envelopes/" + keyId + "/guardian-" + guardianId;
    }

    private static String userEnvelopePath(String keyId, Long userId) {
        return "cap2/key-envelopes/" + keyId + "/user-" + userId;
    }
}
