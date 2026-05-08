package com.oncare.oncare24.inactivity.repository;

import com.oncare.oncare24.inactivity.entity.InactivityDetectionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InactivityDetectionRuleRepository extends JpaRepository<InactivityDetectionRule, Long> {

    Optional<InactivityDetectionRule> findByWardId(Long wardId);

    Optional<InactivityDetectionRule> findByWardIdAndActiveTrue(Long wardId);

    List<InactivityDetectionRule> findByActiveTrueOrderByWardIdAsc();

    boolean existsByWardId(Long wardId);
}
