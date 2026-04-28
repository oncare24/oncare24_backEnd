package com.oncare.oncare24.location.repository;

import com.oncare.oncare24.location.entity.ZoneVisitState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZoneVisitStateRepository extends JpaRepository<ZoneVisitState, Long> {

    /** 한 ward의 모든 zone 상태 조회 (지오펜스 검사 시 한 번에 로드). */
    List<ZoneVisitState> findByWardId(Long wardId);

    /** SafetyZoneResponse 확장 시 단건 조회용. */
    Optional<ZoneVisitState> findByWardIdAndZoneId(Long wardId, Long zoneId);
}