package com.oncare.oncare24.safetyzone.repository;

import com.oncare.oncare24.safetyzone.entity.SafetyZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * SafetyZone Repository.
 * <p>
 * <b>모든 조회는 active=true 조건을 기본으로 깐다</b> (soft delete 정책).
 * 메서드명에 ActiveTrue를 붙여 명시.
 */
public interface SafetyZoneRepository extends JpaRepository<SafetyZone, Long> {

    /** 단건 조회 — soft delete된 zone은 None. */
    Optional<SafetyZone> findByIdAndActiveTrue(Long id);

    /** 특정 피보호자의 모든 활성 안전구역. 등록순. */
    List<SafetyZone> findByWardIdAndActiveTrueOrderByCreatedAtAsc(Long wardId);

    /** 5개 제한 검증용. 같은 wardId의 활성 zone 수. */
    long countByWardIdAndActiveTrue(Long wardId);
}