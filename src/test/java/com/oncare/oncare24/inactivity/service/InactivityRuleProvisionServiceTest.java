package com.oncare.oncare24.inactivity.service;

import com.oncare.oncare24.inactivity.entity.InactivityDetectionRule;
import com.oncare.oncare24.inactivity.repository.InactivityDetectionRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InactivityRuleProvisionServiceTest {

    private static final Long WARD_ID = 88L;

    @Mock
    private InactivityDetectionRuleRepository inactivityDetectionRuleRepository;

    private InactivityRuleProvisionService inactivityRuleProvisionService;

    @BeforeEach
    void setUp() {
        inactivityRuleProvisionService = new InactivityRuleProvisionService(inactivityDetectionRuleRepository);
    }

    @Test
    void provisionDefaultRuleCreatesRuleWithDefaultValues() {
        when(inactivityDetectionRuleRepository.findByWardId(WARD_ID)).thenReturn(Optional.empty());
        when(inactivityDetectionRuleRepository.save(any(InactivityDetectionRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InactivityDetectionRule rule = inactivityRuleProvisionService.provisionDefaultRule(WARD_ID);

        ArgumentCaptor<InactivityDetectionRule> ruleCaptor = ArgumentCaptor.forClass(InactivityDetectionRule.class);
        verify(inactivityDetectionRuleRepository).save(ruleCaptor.capture());
        assertThat(rule).isSameAs(ruleCaptor.getValue());
        assertThat(rule.getWardId()).isEqualTo(WARD_ID);
        assertThat(rule.getExpectedReportIntervalMinutes()).isEqualTo(30);
        assertThat(rule.getStaleLocationWarningMinutes()).isEqualTo(120);
        assertThat(rule.getStaleLocationDangerMinutes()).isEqualTo(360);
        assertThat(rule.getWarningMinutes()).isEqualTo(240);
        assertThat(rule.getDangerMinutes()).isEqualTo(480);
        assertThat(rule.getMinMovementMeters()).isEqualTo(30.0);
        assertThat(rule.getMaxAccuracyMeters()).isEqualTo(100.0);
        assertThat(rule.isActive()).isTrue();
    }

    @Test
    void provisionDefaultRuleDoesNotCreateDuplicateWhenRuleAlreadyExists() {
        InactivityDetectionRule existing = InactivityDetectionRule.builder()
                .wardId(WARD_ID)
                .build();
        when(inactivityDetectionRuleRepository.findByWardId(WARD_ID)).thenReturn(Optional.of(existing));

        InactivityDetectionRule rule = inactivityRuleProvisionService.provisionDefaultRule(WARD_ID);

        assertThat(rule).isSameAs(existing);
        verify(inactivityDetectionRuleRepository, never()).save(any());
    }
}
