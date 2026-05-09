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
    public EncryptedActivityLog saveEncryptedActivityLog(
            Long wardId,
            ActivityEventType eventType,
            String sourceTable,
            Long sourceId,
            LocalDateTime occurredAt,
            EncryptedPayload encryptedPayload
    ) {
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
        return encryptedActivityLogRepository.save(log);
    }
}
