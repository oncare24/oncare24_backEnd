package com.oncare.oncare24.medication.repository;

import com.oncare.oncare24.medication.entity.MedicationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

    List<MedicationLog> findByWardIdAndTakenAtBetweenOrderByTakenAtDesc(
            Long wardId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<MedicationLog> findByWardIdAndTakenAtBetweenOrderByTakenAtAsc(
            Long wardId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<MedicationLog> findByScheduleIdInAndTakenAtBetweenOrderByTakenAtAsc(
            List<Long> scheduleIds,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<MedicationLog> findFirstByScheduleIdAndTakenAtBetweenOrderByTakenAtAsc(
            Long scheduleId,
            LocalDateTime from,
            LocalDateTime to
    );

    List<MedicationLog> findByWardIdOrderByTakenAtDesc(Long wardId);

    List<MedicationLog> findByScheduleIdOrderByTakenAtDesc(Long scheduleId);
}
