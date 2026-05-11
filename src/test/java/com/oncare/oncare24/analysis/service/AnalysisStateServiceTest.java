package com.oncare.oncare24.analysis.service;

import com.oncare.oncare24.analysis.entity.AnalysisType;
import com.oncare.oncare24.analysis.entity.WardAnalysisState;
import com.oncare.oncare24.analysis.repository.WardAnalysisStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisStateServiceTest {

    @Mock
    private WardAnalysisStateRepository wardAnalysisStateRepository;

    @Test
    void upsertLatestStateInsertsWhenStateDoesNotExist() {
        AnalysisStateService service = new AnalysisStateService(wardAnalysisStateRepository);
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 5, 11, 9, 0);

        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(1L, AnalysisType.MEDICATION))
                .thenReturn(Optional.empty());

        service.upsertLatestState(1L, AnalysisType.MEDICATION, 0, analyzedAt);

        ArgumentCaptor<WardAnalysisState> captor = ArgumentCaptor.forClass(WardAnalysisState.class);
        verify(wardAnalysisStateRepository).save(captor.capture());
        WardAnalysisState saved = captor.getValue();
        assertThat(saved.getWardId()).isEqualTo(1L);
        assertThat(saved.getAnalysisType()).isEqualTo(AnalysisType.MEDICATION);
        assertThat(saved.getStatusCode()).isZero();
        assertThat(saved.getAnalyzedAt()).isEqualTo(analyzedAt);
    }

    @Test
    void upsertLatestStateUpdatesExistingStateInsteadOfCreatingAnotherRow() {
        AnalysisStateService service = new AnalysisStateService(wardAnalysisStateRepository);
        LocalDateTime previousAt = LocalDateTime.of(2026, 5, 11, 8, 0);
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 5, 11, 9, 0);
        WardAnalysisState existing = WardAnalysisState.builder()
                .wardId(1L)
                .analysisType(AnalysisType.INACTIVITY)
                .statusCode(0)
                .analyzedAt(previousAt)
                .build();
        ReflectionTestUtils.setField(existing, "id", 100L);

        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(1L, AnalysisType.INACTIVITY))
                .thenReturn(Optional.of(existing));

        service.upsertLatestState(1L, AnalysisType.INACTIVITY, 2, analyzedAt);

        ArgumentCaptor<WardAnalysisState> captor = ArgumentCaptor.forClass(WardAnalysisState.class);
        verify(wardAnalysisStateRepository).save(captor.capture());
        WardAnalysisState saved = captor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getStatusCode()).isEqualTo(2);
        assertThat(saved.getAnalyzedAt()).isEqualTo(analyzedAt);
    }
}
