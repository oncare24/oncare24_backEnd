package com.oncare.oncare24.analysis.service;

import com.oncare.oncare24.analysis.entity.AnalysisType;
import com.oncare.oncare24.analysis.entity.WardAnalysisState;
import com.oncare.oncare24.analysis.repository.WardAnalysisStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AnalysisStateService {

    private final WardAnalysisStateRepository wardAnalysisStateRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WardAnalysisState upsertLatestState(
            Long wardId,
            AnalysisType analysisType,
            int statusCode,
            LocalDateTime analyzedAt
    ) {
        WardAnalysisState state = wardAnalysisStateRepository
                .findByWardIdAndAnalysisType(wardId, analysisType)
                .orElseGet(() -> WardAnalysisState.builder()
                        .wardId(wardId)
                        .analysisType(analysisType)
                        .statusCode(statusCode)
                        .analyzedAt(analyzedAt)
                        .build());
        state.updateStatus(statusCode, analyzedAt);
        return wardAnalysisStateRepository.save(state);
    }
}
