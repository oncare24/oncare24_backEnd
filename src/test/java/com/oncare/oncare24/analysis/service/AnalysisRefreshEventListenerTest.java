package com.oncare.oncare24.analysis.service;

import com.oncare.oncare24.analysis.event.InactivityAnalysisRefreshRequestedEvent;
import com.oncare.oncare24.analysis.event.MedicationAnalysisRefreshRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalysisRefreshEventListenerTest {

    private static final Long WARD_ID = 88L;

    @Mock
    private AnalysisRefreshService analysisRefreshService;

    private AnalysisRefreshEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AnalysisRefreshEventListener(analysisRefreshService);
    }

    @Test
    void inactivityRefreshFailureDoesNotPropagate() {
        doThrow(new IllegalStateException("rule missing"))
                .when(analysisRefreshService)
                .refreshInactivityState(WARD_ID);

        assertThatNoException().isThrownBy(() ->
                listener.refreshInactivityState(new InactivityAnalysisRefreshRequestedEvent(WARD_ID)));

        verify(analysisRefreshService).refreshInactivityState(WARD_ID);
    }

    @Test
    void medicationRefreshFailureDoesNotPropagate() {
        doThrow(new IllegalStateException("decrypt failed"))
                .when(analysisRefreshService)
                .refreshMedicationState(WARD_ID);

        assertThatNoException().isThrownBy(() ->
                listener.refreshMedicationState(new MedicationAnalysisRefreshRequestedEvent(WARD_ID)));

        verify(analysisRefreshService).refreshMedicationState(WARD_ID);
    }
}
