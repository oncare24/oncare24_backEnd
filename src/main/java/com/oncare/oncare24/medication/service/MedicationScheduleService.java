package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.event.MedicationAnalysisRefreshRequestedEvent;
import com.oncare.oncare24.analysis.service.EncryptedSourceEventService;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.dto.CreateMedicationScheduleRequest;
import com.oncare.oncare24.medication.dto.MedicationSchedulePayload;
import com.oncare.oncare24.medication.dto.MedicationScheduleResponse;
import com.oncare.oncare24.medication.dto.UpdateMedicationScheduleRequest;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicationScheduleService {

    private final MedicationScheduleRepository medicationScheduleRepository;
    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;
    private final EncryptedSourceEventService encryptedSourceEventService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MedicationScheduleResponse create(Long currentUserId, CreateMedicationScheduleRequest request) {
        List<DayOfWeek> daysOfWeek = normalizeDaysOfWeek(request.scheduleType(), request.dayOfWeek(), request.daysOfWeek());
        validateAllowanceMinutes(request.allowedEarlyMinutes(), request.allowedDelayMinutes());
        assertCanAccessWard(currentUserId, request.wardId());

        List<MedicationSchedule> savedSchedules = new ArrayList<>();
        List<MedicationSchedulePayload> savedPayloads = new ArrayList<>();
        for (DayOfWeek dayOfWeek : daysOfWeek) {
            MedicationSchedule saved = medicationScheduleRepository.save(MedicationSchedule.builder()
                    .wardId(request.wardId())
                    .build());
            MedicationSchedulePayload payload = schedulePayload(
                    "CREATED",
                    saved.getId(),
                    request.medicationName(),
                    request.scheduledTime(),
                    request.allowedEarlyMinutes(),
                    request.allowedDelayMinutes(),
                    request.scheduleType(),
                    dayOfWeek,
                    dayOfWeek == null ? List.of() : List.of(dayOfWeek),
                    true
            );
            EncryptedActivityLog encryptedLog = saveEncryptedScheduleEvent(saved, payload);
            saved.linkEncryptedActivityLog(encryptedLog.getId());
            savedSchedules.add(saved);
            savedPayloads.add(payload);
        }

        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(request.wardId()));
        List<Long> scheduleIds = savedSchedules.stream().map(MedicationSchedule::getId).toList();
        MedicationSchedule firstSchedule = savedSchedules.get(0);
        MedicationSchedulePayload firstPayload = savedPayloads.get(0);
        List<DayOfWeek> responseDays = request.scheduleType() == MedicationScheduleType.DAILY ? List.of() : daysOfWeek;
        return MedicationScheduleResponse.from(firstSchedule, firstPayload, scheduleIds, responseDays);
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

        if (Boolean.TRUE.equals(request.active())) {
            schedule.activate();
        } else {
            schedule.deactivate();
        }

        MedicationSchedulePayload payload = schedulePayload(
                "UPDATED",
                schedule.getId(),
                request.medicationName(),
                request.scheduledTime(),
                request.allowedEarlyMinutes(),
                request.allowedDelayMinutes(),
                request.scheduleType(),
                request.dayOfWeek(),
                request.dayOfWeek() == null ? List.of() : List.of(request.dayOfWeek()),
                schedule.isActive()
        );
        EncryptedActivityLog encryptedLog = saveEncryptedScheduleEvent(schedule, payload);
        schedule.linkEncryptedActivityLog(encryptedLog.getId());
        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(schedule.getWardId()));
        return MedicationScheduleResponse.from(schedule, payload);
    }

    @Transactional
    public void deactivate(Long currentUserId, Long scheduleId) {
        MedicationSchedule schedule = getScheduleOrThrow(scheduleId);
        assertCanAccessWard(currentUserId, schedule.getWardId());

        schedule.deactivate();
        MedicationSchedulePayload payload = schedulePayload(
                "DEACTIVATED",
                schedule.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false
        );
        EncryptedActivityLog encryptedLog = saveEncryptedScheduleEvent(schedule, payload);
        schedule.linkEncryptedActivityLog(encryptedLog.getId());
        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(schedule.getWardId()));
    }

    private EncryptedActivityLog saveEncryptedScheduleEvent(MedicationSchedule schedule, MedicationSchedulePayload payload) {
        LocalDateTime occurredAt = payload.scheduledTime() != null
                ? LocalDateTime.of(LocalDate.now(), payload.scheduledTime())
                : LocalDateTime.now();
        return encryptedSourceEventService.saveRequiredSourceEvent(
                schedule.getWardId(),
                ActivityEventType.MEDICATION_EVENT,
                "medication_schedule",
                schedule.getId(),
                occurredAt,
                payload
        );
    }

    private MedicationSchedulePayload schedulePayload(
            String action,
            Long scheduleId,
            String medicationName,
            java.time.LocalTime scheduledTime,
            Integer allowedEarlyMinutes,
            Integer allowedDelayMinutes,
            MedicationScheduleType scheduleType,
            java.time.DayOfWeek dayOfWeek,
            List<DayOfWeek> daysOfWeek,
            boolean active
    ) {
        return new MedicationSchedulePayload(
                action,
                scheduleId,
                medicationName,
                scheduledTime,
                allowedEarlyMinutes != null ? allowedEarlyMinutes : 10,
                allowedDelayMinutes != null ? allowedDelayMinutes : 30,
                scheduleType,
                dayOfWeek,
                daysOfWeek,
                active
        );
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

    private List<DayOfWeek> normalizeDaysOfWeek(
            MedicationScheduleType scheduleType,
            DayOfWeek dayOfWeek,
            List<DayOfWeek> daysOfWeek
    ) {
        if (scheduleType == MedicationScheduleType.DAILY) {
            return Collections.singletonList(null);
        }
        LinkedHashSet<DayOfWeek> normalized = new LinkedHashSet<>();
        if (daysOfWeek != null) {
            normalized.addAll(daysOfWeek);
        }
        if (dayOfWeek != null) {
            normalized.add(dayOfWeek);
        }
        normalized.remove(null);
        if (normalized.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_MEDICATION_REQUEST, "WEEKLY schedule requires dayOfWeek or daysOfWeek.");
        }
        return List.copyOf(normalized);
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
