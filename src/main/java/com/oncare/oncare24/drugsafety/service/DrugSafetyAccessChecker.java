package com.oncare.oncare24.drugsafety.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복약 안전 분석 권한 검증 헬퍼.
 * <p>
 * - 처방전 인증(자기 주민번호 + 카카오 본인 인증)은 피보호자(ELDER) 본인만 가능.
 * - 분석 결과 조회는 본인 또는 ACCEPTED 매칭된 보호자만 가능.
 */
@Component
@RequiredArgsConstructor
public class DrugSafetyAccessChecker {

    private final UserRepository userRepository;
    private final GuardianWardRepository guardianWardRepository;

    /**
     * 본인 처방전 인증 권한 — 피보호자(ELDER)만 허용.
     */
    @Transactional(readOnly = true)
    public void ensureSelfAuthAllowed(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.ROLE_NOT_ELDER);
        }
    }

    /**
     * 분석 결과 조회 권한.
     * - viewerId == ownerId  : 본인 조회 통과
     * - 그 외                : (viewer, owner) ACCEPTED 매칭이 있어야 통과 (보호자 시점)
     */
    @Transactional(readOnly = true)
    public void ensureReadAllowed(Long viewerId, Long ownerId) {
        if (viewerId.equals(ownerId)) {
            return;
        }
        boolean linked = guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                viewerId, ownerId, GuardianWardStatus.ACCEPTED
        );
        if (!linked) {
            throw new CustomException(ErrorCode.DRUG_ANALYSIS_ACCESS_DENIED);
        }
    }

    /**
     * 보호자가 피보호자에게 재분석을 요청할 수 있는지 검증.
     * 통과 시 보호자 이름을 반환 — 알림 본문 작성에 사용.
     *
     * 검증 항목:
     *  - 자기 자신에게 요청 금지
     *  - 요청자가 GUARDIAN 역할
     *  - (guardianId, wardId) ACCEPTED 매칭 존재
     */
    @Transactional(readOnly = true)
    public String ensureGuardianRefreshRequestAllowed(Long guardianId, Long wardId) {
        if (guardianId.equals(wardId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        User guardian = userRepository.findById(guardianId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (guardian.getRole() != UserRole.GUARDIAN) {
            throw new CustomException(ErrorCode.ROLE_NOT_GUARDIAN);
        }

        boolean linked = guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                guardianId, wardId, GuardianWardStatus.ACCEPTED
        );
        if (!linked) {
            throw new CustomException(ErrorCode.NOT_LINKED_TO_WARD);
        }

        return guardian.getName();
    }
}