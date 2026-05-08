package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.dto.CreateMedicationLogRequest;
import com.oncare.oncare24.medication.dto.MedicationLogResponse;
import com.oncare.oncare24.medication.entity.MedicationLog;
import com.oncare.oncare24.medication.entity.MedicationLogSource;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.repository.MedicationLogRepository;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MedicationLogService {

    private final MedicationLogRepository medicationLogRepository;
    private final MedicationScheduleRepository medicationScheduleRepository;
    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;

    @Transactional
    public MedicationLogResponse create(Long currentUserId, CreateMedicationLogRequest request) {
        User currentUser = assertCanAccessWard(currentUserId, request.wardId());

        MedicationSchedule schedule = null;
        if (request.scheduleId() != null) {
            schedule = medicationScheduleRepository.findById(request.scheduleId())
                    .orElseThrow(() -> new CustomException(ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND));
            validateScheduleForLog(schedule, request.wardId());
        }

        MedicationLogSource logSource = resolveLogSource(request.logSource());
        if (currentUser.getRole() == UserRole.GUARDIAN && logSource == MedicationLogSource.USER_INPUT) {
            logSource = MedicationLogSource.GUARDIAN_INPUT;
        }

        String medicationName = schedule != null
                ? schedule.getMedicationName()
                : request.medicationName();
        if (!StringUtils.hasText(medicationName)) {
            throw new CustomException(ErrorCode.INVALID_MEDICATION_REQUEST, "medicationName is required without scheduleId.");
        }

        MedicationLog log = MedicationLog.builder()
                .wardId(request.wardId())
                .scheduleId(schedule != null ? schedule.getId() : null)
                .takenAt(request.takenAt() != null ? request.takenAt() : LocalDateTime.now())
                .medicationName(medicationName)
                .logSource(logSource)
                .build();

        return MedicationLogResponse.from(medicationLogRepository.save(log));
    }

    private void validateScheduleForLog(MedicationSchedule schedule, Long wardId) {
        if (!schedule.getWardId().equals(wardId)) {
            throw new CustomException(ErrorCode.INVALID_MEDICATION_REQUEST, "scheduleId does not belong to wardId.");
        }
        if (!schedule.isActive()) {
            throw new CustomException(ErrorCode.INVALID_MEDICATION_REQUEST, "Inactive medication schedule cannot be logged.");
        }
    }

    private MedicationLogSource resolveLogSource(MedicationLogSource logSource) {
        MedicationLogSource resolved = logSource != null ? logSource : MedicationLogSource.USER_INPUT;
        if (resolved == MedicationLogSource.SYSTEM) {
            throw new CustomException(ErrorCode.INVALID_MEDICATION_REQUEST, "SYSTEM logSource is not allowed from this API.");
        }
        return resolved;
    }

    private User assertCanAccessWard(Long currentUserId, Long wardId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ELDER));

        if (ward.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.INVALID_ELDER);
        }

        if (currentUser.getRole() == UserRole.ELDER && currentUserId.equals(wardId)) {
            return currentUser;
        }

        if (currentUser.getRole() == UserRole.GUARDIAN) {
            boolean linked = guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                    currentUserId,
                    wardId,
                    GuardianWardStatus.ACCEPTED
            );
            if (linked) {
                return currentUser;
            }
        }

        throw new CustomException(ErrorCode.MEDICATION_ACCESS_DENIED);
    }
}
