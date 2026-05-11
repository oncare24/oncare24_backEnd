package com.oncare.oncare24.analysis.repository;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EncryptedActivityLogRepository extends JpaRepository<EncryptedActivityLog, Long> {

    List<EncryptedActivityLog> findByWardIdAndEventTypeAndOccurredAtBetweenOrderByOccurredAtDesc(
            Long wardId,
            ActivityEventType eventType,
            LocalDateTime from,
            LocalDateTime to
    );

    List<EncryptedActivityLog> findByWardIdOrderByOccurredAtDesc(Long wardId);

    Optional<EncryptedActivityLog> findBySourceTableAndSourceIdAndEventType(
            String sourceTable,
            Long sourceId,
            ActivityEventType eventType
    );

    Optional<EncryptedActivityLog> findFirstBySourceTableAndSourceIdAndEventTypeOrderByOccurredAtDesc(
            String sourceTable,
            Long sourceId,
            ActivityEventType eventType
    );

    Optional<EncryptedActivityLog> findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
            Long wardId,
            ActivityEventType eventType,
            String sourceTable,
            LocalDateTime occurredAt
    );

    List<EncryptedActivityLog> findByWardIdAndEventTypeAndSourceTableAndOccurredAtBetweenOrderByOccurredAtAsc(
            Long wardId,
            ActivityEventType eventType,
            String sourceTable,
            LocalDateTime from,
            LocalDateTime to
    );

    List<EncryptedActivityLog> findByWardIdAndEventTypeAndSourceTableOrderByOccurredAtAsc(
            Long wardId,
            ActivityEventType eventType,
            String sourceTable
    );
}
