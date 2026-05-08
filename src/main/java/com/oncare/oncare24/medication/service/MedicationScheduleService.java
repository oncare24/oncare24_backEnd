package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.dto.CreateMedicationScheduleRequest;
import com.oncare.oncare24.medication.dto.MedicationScheduleResponse;
import com.oncare.oncare24.medication.dto.UpdateMedicationScheduleRequest;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicationScheduleService {

    private final MedicationScheduleRepository medicationScheduleRepository;
    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;

    @Transactional
    public MedicationScheduleResponse create(Long currentUserId, CreateMedicationScheduleRequest request) {
        validateScheduleType(request.scheduleType(), request.dayOfWeek());
        validateAllowanceMinutes(request.allowedEarlyMinutes(), request.allowedDelayMinutes());
        assertCanAccessWard(currentUserId, request.wardId());

        MedicationSchedule schedule = MedicationSchedule.builder()
                .wardId(request.wardId())
                .medicationName(request.medicationName())
                .scheduledTime(request.scheduledTime())
                .allowedEarlyMinutes(request.allowedEarlyMinutes())
                .allowedDelayMinutes(request.allowedDelayMinutes())
                .scheduleType(request.scheduleType())
                .dayOfWeek(request.dayOfWeek())
                .build();

        return MedicationScheduleResponse.from(medicationScheduleRepository.save(schedule));
    }

    @Transactional(readOnly = true)
    public List<MedicationScheduleResponse> findAllByWard(Long currentUserId, Long wardId) {
        assertCanAccessWard(currentUserId, wardId);

        return medicationScheduleRepository.findByWardIdOrderByScheduledTimeAsc(wardId)
                .stream()
                .map(MedicationScheduleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicationScheduleResponse findById(Long currentUserId, Long scheduleId) {
        MedicationSchedule schedule = getScheduleOrThrow(scheduleId);
        assertCanAccessWard(currentUserId, schedule.getWardId());

        return MedicationScheduleResponse.from(schedule);
    }

    @Transactional
    public MedicationScheduleResponse update(
            Long currentUserId,
            Long scheduleId,
            UpdateMedicationScheduleRequest request
    ) {
        validateScheduleType(request.scheduleType(), request.dayOfWeek());
        validateAllowanceMinutes(request.allowedEarlyMinutes(), request.allowedDelayMinutes());

        MedicationSchedule schedule = getScheduleOrThrow(scheduleId);
        assertCanAccessWard(currentUserId, schedule.getWardId());

        schedule.update(
                request.medicationName(),
                request.scheduledTime(),
                request.allowedEarlyMinutes(),
                request.allowedDelayMinutes(),
                request.scheduleType(),
                request.dayOfWeek()
        );
        if (Boolean.TRUE.equals(request.active())) {
            schedule.activate();
        } else {
            schedule.deactivate();
        }

        return MedicationScheduleResponse.from(schedule);
    }

    @Transactional
    public void deactivate(Long currentUserId, Long scheduleId) {
        MedicationSchedule schedule = getScheduleOrThrow(scheduleId);
        assertCanAccessWard(currentUserId, schedule.getWardId());

        schedule.deactivate();
    }

    private MedicationSchedule getScheduleOrThrow(Long scheduleId) {
        return medicationScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND));
    }

    private void validateScheduleType(MedicationScheduleType scheduleType, java.time.DayOfWeek dayOfWeek) {
        if (scheduleType == MedicationScheduleType.WEEKLY && dayOfWeek == null) {
            throw new CustomException(ErrorCode.INVALID_MEDICATION_REQUEST, "WEEKLY schedule requires dayOfWeek.");
        }
    }

    private void validateAllowanceMinutes(Integer allowedEarlyMinutes, Integer allowedDelayMinutes) {
        if ((allowedEarlyMinutes != null && allowedEarlyMinutes < 0)
                || (allowedDelayMinutes != null && allowedDelayMinutes < 0)) {
            throw new CustomException(ErrorCode.INVALID_MEDICATION_REQUEST, "Allowed minutes must be zero or positive.");
        }
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

        if (currentUser.getRole() == UserRole.GUARDIAN) {
            boolean linked = guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                    currentUserId,
                    wardId,
                    GuardianWardStatus.ACCEPTED
            );
            if (linked) {
                return;
            }
        }

        throw new CustomException(ErrorCode.MEDICATION_ACCESS_DENIED);
    }
}
