package com.oncare.oncare24.analysis.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.security.crypto.dto.EncryptedPayload;
import com.oncare.oncare24.security.crypto.service.CommonCryptoService;
import com.oncare.oncare24.security.envelope.KeyEnvelopeProvisionService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptedSourceEventService {
    private final GuardianWardRepository guardianWardRepository;
    private final MlKemKeyProvisionService mlKemKeyProvisionService;
    private final CommonCryptoService commonCryptoService;
    private final EncryptedActivityLogService encryptedActivityLogService;
    private final KeyEnvelopeProvisionService keyEnvelopeProvisionService;

    @Value("${oncare.security.crypto.enabled:false}")
    private boolean cryptoEnabled;

    public void saveSourceEvent(
            Long wardId,
            ActivityEventType eventType,
            String sourceTable,
            Long sourceId,
            LocalDateTime occurredAt,
            Object payload
    ) {
        if (!cryptoEnabled) {
            return;
        }

        List<GuardianWard> guardians = guardianWardRepository.findByWardIdAndStatus(
                wardId,
                GuardianWardStatus.ACCEPTED
        );
        try {
            EncryptedPayload encryptedPayload = encryptForWard(
                    payload,
                    wardId,
                    eventType,
                    occurredAt,
                    sourceTable,
                    sourceId,
                    guardians
            );
            encryptedActivityLogService.saveEncryptedActivityLog(
                    wardId,
                    eventType,
                    sourceTable,
                    sourceId,
                    occurredAt,
                    encryptedPayload
            );
        } catch (RuntimeException error) {
            log.warn(
                    "[EncryptedSourceEvent] save failed. wardId={}, eventType={}, sourceTable={}, sourceId={}, message={}",
                    wardId,
                    eventType,
                    sourceTable,
                    sourceId,
                    error.getMessage()
            );
        }
    }

    public EncryptedActivityLog saveRequiredSourceEvent(
            Long wardId,
            ActivityEventType eventType,
            String sourceTable,
            Long sourceId,
            LocalDateTime occurredAt,
            Object payload
    ) {
        if (!cryptoEnabled) {
            throw new IllegalStateException("crypto must be enabled to persist required encrypted source event.");
        }

        List<GuardianWard> guardians = guardianWardRepository.findByWardIdAndStatus(
                wardId,
                GuardianWardStatus.ACCEPTED
        );

        EncryptedPayload encryptedPayload = encryptForWard(
                payload,
                wardId,
                eventType,
                occurredAt,
                sourceTable,
                sourceId,
                guardians
        );
        return encryptedActivityLogService.saveEncryptedActivityLog(
                wardId,
                eventType,
                sourceTable,
                sourceId,
                occurredAt,
                encryptedPayload
        );
    }

    private EncryptedPayload encryptForWard(
            Object payload,
            Long wardId,
            ActivityEventType eventType,
            LocalDateTime occurredAt,
            String sourceTable,
            Long sourceId,
            List<GuardianWard> guardians
    ) {
        byte[] wardPublicKey = mlKemKeyProvisionService.readPublicKey(wardId);
        EncryptedPayload encryptedPayload;
        if (guardians.isEmpty()) {
            encryptedPayload = commonCryptoService.encryptForUser(
                    payload,
                    wardId,
                    eventType.name(),
                    occurredAt,
                    sourceTable,
                    sourceId,
                    wardId,
                    wardPublicKey
            );
        } else {
            GuardianWard primaryGuardian = guardians.get(0);
            byte[] guardianPublicKey = mlKemKeyProvisionService.readPublicKey(primaryGuardian.getGuardianId());
            encryptedPayload = commonCryptoService.encryptForUserAndGuardian(
                    payload,
                    wardId,
                    eventType.name(),
                    occurredAt,
                    sourceTable,
                    sourceId,
                    wardId,
                    wardPublicKey,
                    primaryGuardian.getGuardianId(),
                    guardianPublicKey
            );
        }

        keyEnvelopeProvisionService.provisionUserEnvelopeForDataKeyId(wardId, encryptedPayload.dataKeyId());
        for (GuardianWard guardian : guardians) {
            keyEnvelopeProvisionService.provisionGuardianEnvelopeForDataKeyId(
                    wardId,
                    guardian.getGuardianId(),
                    encryptedPayload.dataKeyId()
            );
        }
        return encryptedPayload;
    }
}
