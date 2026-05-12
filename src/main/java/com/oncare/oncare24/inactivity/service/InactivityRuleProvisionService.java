package com.oncare.oncare24.inactivity.service;

import com.oncare.oncare24.inactivity.entity.InactivityDetectionRule;
import com.oncare.oncare24.inactivity.repository.InactivityDetectionRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InactivityRuleProvisionService {

    private final InactivityDetectionRuleRepository inactivityDetectionRuleRepository;

    @Transactional
    public InactivityDetectionRule provisionDefaultRule(Long wardId) {
        return inactivityDetectionRuleRepository.findByWardId(wardId)
                .map(existing -> {
                    if (!existing.isActive()) {
                        existing.update(240, 480, 120, 360, 30, 30.0, 100.0);
                        existing.activate();
                    }
                    return existing;
                })
                .orElseGet(() -> inactivityDetectionRuleRepository.save(
                        InactivityDetectionRule.builder()
                                .wardId(wardId)
                                .expectedReportIntervalMinutes(30)
                                .staleLocationWarningMinutes(120)
                                .staleLocationDangerMinutes(360)
                                .warningMinutes(240)
                                .dangerMinutes(480)
                                .minMovementMeters(30.0)
                                .maxAccuracyMeters(100.0)
                                .build()
                ));
    }
}
