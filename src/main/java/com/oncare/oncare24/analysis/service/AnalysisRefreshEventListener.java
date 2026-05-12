package com.oncare.oncare24.analysis.service;

import com.oncare.oncare24.analysis.event.InactivityAnalysisRefreshRequestedEvent;
import com.oncare.oncare24.analysis.event.MedicationAnalysisRefreshRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisRefreshEventListener {

    private final AnalysisRefreshService analysisRefreshService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refreshInactivityState(InactivityAnalysisRefreshRequestedEvent event) {
        try {
            analysisRefreshService.refreshInactivityState(event.wardId());
        } catch (RuntimeException error) {
            log.warn("[ANALYSIS-REFRESH] inactivity listener failed. wardId={}, message={}",
                    event.wardId(), error.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refreshMedicationState(MedicationAnalysisRefreshRequestedEvent event) {
        try {
            analysisRefreshService.refreshMedicationState(event.wardId());
        } catch (RuntimeException error) {
            log.warn("[ANALYSIS-REFRESH] medication listener failed. wardId={}, message={}",
                    event.wardId(), error.getMessage());
        }
    }
}
