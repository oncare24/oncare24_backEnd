package com.oncare.oncare24.drugsafety.repository;

import com.oncare.oncare24.drugsafety.entity.MedicationAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicationAnalysisRepository extends JpaRepository<MedicationAnalysis, Long> {

    Optional<MedicationAnalysis> findByUserId(Long userId);
}