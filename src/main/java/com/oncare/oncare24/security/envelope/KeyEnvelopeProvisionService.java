package com.oncare.oncare24.security.envelope;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
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
    private final EncryptedActivityLogRepository encryptedActivityLogRepository;

    @Value("${oncare.security.crypto.enabled:false}")
    private boolean cryptoEnabled;

    public void provisionForAcceptedGuardian(Long wardId, Long guardianId) {
        if (!cryptoEnabled) {
            log.info("Crypto provisioning skipped because disabled");
            return;
        }

        DataKeyProvisionService.ProvisionedDataKey dataKey = dataKeyProvisionService.getOrCreateTodayDataKey();
        provisionGuardianEnvelopeForDataKey(wardId, guardianId, dataKey);
        provisionGuardianAccessForExistingWardLogs(guardianId, wardId);
    }

    /**
     * Team policy: once a guardian-ward relationship becomes ACCEPTED, the guardian receives
     * retroactive access to the ward's existing encrypted activity logs. ACCEPTED relationship
     * checks remain in each query service; this method only provisions missing key envelopes.
     */
    public int provisionGuardianAccessForExistingWardLogs(Long guardianId, Long wardId) {
        if (!cryptoEnabled) {
            log.info("Crypto provisioning skipped because disabled");
            return 0;
        }

        Set<String> dataKeyIds = new LinkedHashSet<>(
                encryptedActivityLogRepository.findDistinctDataKeyIdsByWardId(wardId)
        );
        int created = 0;
        for (String dataKeyId : dataKeyIds) {
            if (dataKeyId == null || dataKeyId.isBlank()) {
                continue;
            }
            String path = guardianEnvelopePath(dataKeyId, guardianId);
            if (openBaoKvClient.exists(path)) {
                continue;
            }
            DataKeyProvisionService.ProvisionedDataKey dataKey = dataKeyProvisionService.getDataKey(dataKeyId);
            provisionGuardianEnvelopeForDataKey(wardId, guardianId, dataKey);
            created++;
        }
        return created;
    }

    private void provisionGuardianEnvelopeForDataKey(
            Long wardId,
            Long guardianId,
            DataKeyProvisionService.ProvisionedDataKey dataKey
    ) {
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
        provisionUserEnvelopeForDataKey(userId, dataKey);
    }

    public void provisionUserEnvelopeForDataKeyId(Long userId, String dataKeyId) {
        if (!cryptoEnabled) {
            log.info("Crypto provisioning skipped because disabled");
            return;
        }
        DataKeyProvisionService.ProvisionedDataKey dataKey = dataKeyProvisionService.getDataKey(dataKeyId);
        provisionUserEnvelopeForDataKey(userId, dataKey);
    }

    public void provisionGuardianEnvelopeForDataKeyId(Long wardId, Long guardianId, String dataKeyId) {
        if (!cryptoEnabled) {
            log.info("Crypto provisioning skipped because disabled");
            return;
        }
        DataKeyProvisionService.ProvisionedDataKey dataKey = dataKeyProvisionService.getDataKey(dataKeyId);
        provisionGuardianEnvelopeForDataKey(wardId, guardianId, dataKey);
    }

    private void provisionUserEnvelopeForDataKey(
            Long userId,
            DataKeyProvisionService.ProvisionedDataKey dataKey
    ) {
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
