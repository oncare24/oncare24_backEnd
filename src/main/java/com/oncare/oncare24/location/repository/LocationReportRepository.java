package com.oncare.oncare24.location.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.oncare.oncare24.location.entity.LocationReport;

public interface LocationReportRepository extends JpaRepository<LocationReport, Long> {
        
    /** 특정 사용자의 최신 위치 1건. 보호자 화면 "마지막 위치" 표시용. */
    Optional<LocationReport> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<LocationReport> findFirstByUserIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime createdAt
    );

    List<LocationReport> findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long userId,
            LocalDateTime from,
            LocalDateTime to
    );
}
