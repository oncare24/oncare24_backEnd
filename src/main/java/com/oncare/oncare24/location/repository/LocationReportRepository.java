package com.oncare.oncare24.location.repository;

import com.oncare.oncare24.location.entity.LocationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationReportRepository extends JpaRepository<LocationReport, Long> {

    /** 특정 사용자의 최신 위치 1건. 보호자 화면 "마지막 위치" 표시용. */
    Optional<LocationReport> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}