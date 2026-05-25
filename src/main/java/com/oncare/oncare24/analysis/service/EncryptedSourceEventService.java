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

    // 선택 원천 이벤트 암호화 저장 진입점
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
            // 원천 이벤트 payload를 ward와 보호자용 암호화 패키지로 변환
            EncryptedPayload encryptedPayload = encryptForWard(
                    payload,
                    wardId,
                    eventType,
                    occurredAt,
                    sourceTable,
                    sourceId,
                    guardians
            );
            // 암호화된 원천 이벤트를 encrypted_activity_log에 저장
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

    // 필수 원천 이벤트 암호화 저장 진입점
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
        // 암호화된 원천 이벤트 저장 결과를 호출자에게 반환
        return encryptedActivityLogService.saveEncryptedActivityLog(
                wardId,
                eventType,
                sourceTable,
                sourceId,
                occurredAt,
                encryptedPayload
        );
    }

    // ward와 보호자 대상 암호화 패키지 생성
    private EncryptedPayload encryptForWard(
            Object payload,
            Long wardId,
            ActivityEventType eventType,
            LocalDateTime occurredAt,
            String sourceTable,
            Long sourceId,
            List<GuardianWard> guardians
    ) {
        // ward 공개키로 복호화 가능한 암호화 수신자 구성
        byte[] wardPublicKey = mlKemKeyProvisionService.readPublicKey(wardId);
        EncryptedPayload encryptedPayload;
        if (guardians.isEmpty()) {
            // 보호자 연결 전 ward 본인용으로 원천 이벤트 암호화
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
            // 보호자 공개키를 함께 사용해 ward와 보호자가 열 수 있는 패키지 생성
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

        // data key에 대한 ward 본인용 envelope 보장
        keyEnvelopeProvisionService.provisionUserEnvelopeForDataKeyId(wardId, encryptedPayload.dataKeyId());
        for (GuardianWard guardian : guardians) {
            // 연결된 보호자별 data key envelope 보장
            keyEnvelopeProvisionService.provisionGuardianEnvelopeForDataKeyId(
                    wardId,
                    guardian.getGuardianId(),
                    encryptedPayload.dataKeyId()
            );
        }
        return encryptedPayload;
    }
}
