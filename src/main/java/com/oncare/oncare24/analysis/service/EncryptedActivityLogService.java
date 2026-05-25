package com.oncare.oncare24.analysis.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.security.crypto.dto.EncryptedPayload;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EncryptedActivityLogService {
    private final EncryptedActivityLogRepository encryptedActivityLogRepository;

    @Transactional
    // encrypted_activity_log 저장 진입점
    public EncryptedActivityLog saveEncryptedActivityLog(
            Long wardId,
            ActivityEventType eventType,
            String sourceTable,
            Long sourceId,
            LocalDateTime occurredAt,
            EncryptedPayload encryptedPayload
    ) {
        // 암호화 패키지와 AAD를 encrypted_activity_log 엔티티로 조립
        EncryptedActivityLog log = EncryptedActivityLog.builder()
                .wardId(wardId)
                .dataKeyId(encryptedPayload.dataKeyId())
                .eventType(eventType)
                .sourceTable(sourceTable)
                .sourceId(sourceId)
                .occurredAt(occurredAt)
                .encryptedPackage(encryptedPayload.encryptedPackage())
                .aadJson(encryptedPayload.aadJson())
                .build();
        // encrypted_activity_log 테이블에 암호화 이벤트 저장
        return encryptedActivityLogRepository.save(log);
    }
}
