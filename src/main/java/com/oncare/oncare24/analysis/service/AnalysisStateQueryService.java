package com.oncare.oncare24.analysis.service;

import com.oncare.oncare24.analysis.dto.AnalysisStateItemResponse;
import com.oncare.oncare24.analysis.dto.AnalysisStateResponse;
import com.oncare.oncare24.analysis.entity.AnalysisType;
import com.oncare.oncare24.analysis.entity.WardAnalysisState;
import com.oncare.oncare24.analysis.repository.WardAnalysisStateRepository;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisStateQueryService {

    private final WardAnalysisStateRepository wardAnalysisStateRepository;
    private final UserRepository userRepository;
    private final GuardianWardRepository guardianWardRepository;

    @Transactional(readOnly = true)
    public AnalysisStateResponse findWardAnalysisState(Long currentUserId, Long wardId) {
        assertCanAccessWard(currentUserId, wardId);

        AnalysisStateItemResponse medication = wardAnalysisStateRepository
                .findByWardIdAndAnalysisType(wardId, AnalysisType.MEDICATION)
                .map(state -> item(state, this::medicationStatusName))
                .orElse(null);
        AnalysisStateItemResponse inactivity = wardAnalysisStateRepository
                .findByWardIdAndAnalysisType(wardId, AnalysisType.INACTIVITY)
                .map(state -> item(state, this::inactivityStatusName))
                .orElse(null);

        return new AnalysisStateResponse(wardId, medication, inactivity);
    }

    private AnalysisStateItemResponse item(WardAnalysisState state, StatusNameMapper mapper) {
        return new AnalysisStateItemResponse(
                state.getStatusCode(),
                mapper.map(state.getStatusCode()),
                state.getAnalyzedAt()
        );
    }

    private String medicationStatusName(int statusCode) {
        return switch (statusCode) {
            case 0 -> "ON_TIME";
            case 1 -> "DELAYED";
            case 2 -> "MISSED";
            default -> "UNKNOWN";
        };
    }

    private String inactivityStatusName(int statusCode) {
        return switch (statusCode) {
            case 0 -> "ACTIVE";
            case 1 -> "INACTIVE";
            case 2 -> "UNKNOWN";
            default -> "UNKNOWN";
        };
    }

    private void assertCanAccessWard(Long currentUserId, Long wardId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ELDER));

        if (ward.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.INVALID_ELDER);
        }

        if (currentUser.getRole() == UserRole.ELDER && currentUserId.equals(wardId)) {
            return;
        }

        if (currentUser.getRole() == UserRole.GUARDIAN
                && guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                currentUserId,
                wardId,
                GuardianWardStatus.ACCEPTED
        )) {
            return;
        }

        throw new CustomException(ErrorCode.NOT_LINKED_TO_WARD);
    }

    @FunctionalInterface
    private interface StatusNameMapper {
        String map(int statusCode);
    }
}
