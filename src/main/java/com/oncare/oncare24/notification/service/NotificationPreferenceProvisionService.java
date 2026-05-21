package com.oncare.oncare24.notification.service;

import com.oncare.oncare24.notification.entity.GuardianNotificationPreference;
import com.oncare.oncare24.notification.repository.GuardianNotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * 보호자 알림 설정 기본행 자동 생성 서비스.
 * <p>
 * <b>호출 시점 2곳</b>
 * <ul>
 *     <li>회원가입 (AuthService.signUp) — role이 GUARDIAN일 때</li>
 *     <li>설정 조회/업데이트 (NotificationPreferenceService) — 행이 없을 때 lazy create</li>
 * </ul>
 * <p>
 * 행 자동 생성 패턴은 InactivityRuleProvisionService(ELDER용)와 동일.
 * 멱등(이미 행 있으면 그대로 반환) — 동시성 충돌 시에도 안전.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceProvisionService {

    /** 기본 다이제스트 시각 = 22:00. Medisafe, CareZone 등 시중 케어 앱 표준 저녁 시각. */
    private static final LocalTime DEFAULT_DIGEST_TIME = LocalTime.of(22, 0);

    private final GuardianNotificationPreferenceRepository preferenceRepository;

    @Transactional
    public GuardianNotificationPreference provisionDefault(Long guardianId) {
        return preferenceRepository.findByGuardianId(guardianId)
                .orElseGet(() -> {
                    GuardianNotificationPreference pref = GuardianNotificationPreference.builder()
                            .guardianId(guardianId)
                            .immediateMedicationAlert(true)
                            .dailyDigestEnabled(true)
                            .dailyDigestTime(DEFAULT_DIGEST_TIME)
                            .build();
                    GuardianNotificationPreference saved = preferenceRepository.save(pref);
                    log.info("[NOTIFY-PREF-PROVISION] guardianId={}, defaults applied", guardianId);
                    return saved;
                });
    }
}