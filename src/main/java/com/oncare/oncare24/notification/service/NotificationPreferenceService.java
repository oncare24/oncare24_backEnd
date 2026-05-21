package com.oncare.oncare24.notification.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.notification.dto.NotificationPreferenceResponse;
import com.oncare.oncare24.notification.dto.UpdateNotificationPreferenceRequest;
import com.oncare.oncare24.notification.entity.GuardianNotificationPreference;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자 알림 설정 조회/업데이트 서비스.
 * <p>
 * <b>역할 검증</b>: GUARDIAN만 호출 가능. ELDER 호출 시 403 (ROLE_NOT_GUARDIAN).
 * <b>lazy create</b>: 행 없으면 default로 생성 후 처리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final UserRepository userRepository;
    private final NotificationPreferenceProvisionService provisionService;

    @Transactional
    public NotificationPreferenceResponse getMy(Long userId) {
        verifyGuardian(userId);
        GuardianNotificationPreference pref = provisionService.provisionDefault(userId);
        return NotificationPreferenceResponse.from(pref);
    }

    @Transactional
    public NotificationPreferenceResponse updateMy(Long userId, UpdateNotificationPreferenceRequest request) {
        verifyGuardian(userId);
        GuardianNotificationPreference pref = provisionService.provisionDefault(userId);

        if (request.immediateMedicationAlert() != null) {
            pref.updateImmediateMedicationAlert(request.immediateMedicationAlert());
        }
        if (request.dailyDigestEnabled() != null) {
            pref.updateDailyDigestEnabled(request.dailyDigestEnabled());
        }
        if (request.dailyDigestTime() != null) {
            pref.updateDailyDigestTime(request.dailyDigestTime());
        }

        log.info("[NOTIFY-PREF-UPDATE] guardianId={}, immediate={}, digest={}, time={}",
                userId,
                pref.isImmediateMedicationAlert(),
                pref.isDailyDigestEnabled(),
                pref.getDailyDigestTime());

        return NotificationPreferenceResponse.from(pref);
    }

    private void verifyGuardian(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.GUARDIAN) {
            throw new CustomException(ErrorCode.ROLE_NOT_GUARDIAN);
        }
    }
}