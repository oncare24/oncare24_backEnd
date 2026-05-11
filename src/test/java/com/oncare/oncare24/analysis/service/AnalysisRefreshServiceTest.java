package com.oncare.oncare24.analysis.service;

import com.oncare.oncare24.inactivity.service.InactivityAnalysisService;
import com.oncare.oncare24.medication.service.MedicationAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalysisRefreshServiceTest {

    private static final Long WARD_ID = 1L;

    @Mock
    private MedicationAnalysisService medicationAnalysisService;

    @Mock
    private InactivityAnalysisService inactivityAnalysisService;

    private AnalysisRefreshService analysisRefreshService;

    @BeforeEach
    void setUp() {
        analysisRefreshService = new AnalysisRefreshService(
                medicationAnalysisService,
                inactivityAnalysisService
        );
    }

    @Test
    void refreshMedicationStateRunsMedicationAnalysis() {
        analysisRefreshService.refreshMedicationState(WARD_ID);

        verify(medicationAnalysisService).analyzeWardMedication(eq(WARD_ID), any(LocalDate.class));
    }

    @Test
    void refreshInactivityStateRunsInactivityAnalysis() {
        analysisRefreshService.refreshInactivityState(WARD_ID);

        verify(inactivityAnalysisService).analyzeWardInactivity(eq(WARD_ID), any(LocalDateTime.class));
    }

    @Test
    void refreshDoesNotPropagateAnalysisFailure() {
        doThrow(new IllegalStateException("analysis failed"))
                .when(medicationAnalysisService)
                .analyzeWardMedication(eq(WARD_ID), any(LocalDate.class));

        assertThatNoException().isThrownBy(() -> analysisRefreshService.refreshMedicationState(WARD_ID));
    }
}
