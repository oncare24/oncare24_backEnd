package com.oncare.oncare24.elder.service;

import com.oncare.oncare24.elder.dto.MyGuardianResponse;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 어르신 시점 — 내 보호자 목록 조회.
 *
 * 용도:
 *  1. 어르신 앱에서 위치 추적 시작 전 보호자 매핑 존재 여부 체크 (0명이면 위치추적·포그라운드 알림 OFF)
 *  2. 향후 어르신 홈 "내 보호자" 섹션 표시
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ElderGuardianService {

    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;

    public List<MyGuardianResponse> findMyGuardians(Long currentUserId) {
        User me = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (me.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.INVALID_ELDER);
        }

        List<GuardianWard> mappings = guardianWardRepository
                .findByWardIdAndStatus(currentUserId, GuardianWardStatus.ACCEPTED);

        return mappings.stream()
                .map(m -> {
                    User guardian = userRepository.findById(m.getGuardianId())
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                    return new MyGuardianResponse(
                            guardian.getId(),
                            guardian.getName(),
                            m.getUpdatedAt()
                    );
                })
                .toList();
    }
}