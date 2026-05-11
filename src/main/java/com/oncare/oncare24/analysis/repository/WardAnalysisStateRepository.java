package com.oncare.oncare24.analysis.repository;

import com.oncare.oncare24.analysis.entity.AnalysisType;
import com.oncare.oncare24.analysis.entity.WardAnalysisState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WardAnalysisStateRepository extends JpaRepository<WardAnalysisState, Long> {

    Optional<WardAnalysisState> findByWardIdAndAnalysisType(Long wardId, AnalysisType analysisType);
}
