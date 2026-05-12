package com.oncare.oncare24.analysis.service;

import com.oncare.oncare24.inactivity.service.InactivityAnalysisService;
import com.oncare.oncare24.medication.service.MedicationAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisRefreshService {

    private final MedicationAnalysisService medicationAnalysisService;
    private final InactivityAnalysisService inactivityAnalysisService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshMedicationState(Long wardId) {
        try {
            medicationAnalysisService.analyzeWardMedication(wardId, LocalDate.now());
        } catch (RuntimeException e) {
            log.warn("[ANALYSIS-REFRESH] medication refresh failed. wardId={}, message={}",
                    wardId, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshInactivityState(Long wardId) {
        try {
            inactivityAnalysisService.analyzeWardInactivity(wardId, LocalDateTime.now());
        } catch (RuntimeException e) {
            log.warn("[ANALYSIS-REFRESH] inactivity refresh failed. wardId={}, message={}",
                    wardId, e.getMessage());
        }
    }
}
