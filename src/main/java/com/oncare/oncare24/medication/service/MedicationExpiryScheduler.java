package com.oncare.oncare24.medication.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class MedicationExpiryScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MedicationScheduleService medicationScheduleService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void deactivateExpired() {
        int n = medicationScheduleService.deactivateExpiredSchedules(LocalDate.now(KST));
        if (n > 0) {
            log.info("[MED-EXPIRE] deactivated {} expired schedule(s)", n);
        }
    }
}