package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.medication.dto.MedicationAnalysisResult;
import com.oncare.oncare24.medication.entity.MedicationAnalysisStatus;
import com.oncare.oncare24.medication.entity.MedicationLog;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.repository.MedicationLogRepository;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicationAnalysisService {

    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicationLogRepository medicationLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MedicationAnalysisResult> analyzeWardMedication(Long wardId, LocalDate analysisDate) {
        assertWardIsElder(wardId);

        List<MedicationSchedule> schedules = medicationScheduleRepository
                .findByWardIdAndActiveTrueOrderByScheduledTimeAsc(wardId);

        return analyzeSchedules(schedules, analysisDate, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<MedicationAnalysisResult> analyzeAllActiveWardMedication(LocalDate analysisDate) {
        List<MedicationSchedule> schedules = medicationScheduleRepository
                .findByActiveTrueOrderByWardIdAscScheduledTimeAsc();

        return analyzeSchedules(schedules, analysisDate, LocalDateTime.now());
    }

    private List<MedicationAnalysisResult> analyzeSchedules(
            List<MedicationSchedule> schedules,
            LocalDate analysisDate,
            LocalDateTime now
    ) {
        List<MedicationSchedule> targetSchedules = schedules.stream()
                .filter(schedule -> isTargetSchedule(schedule, analysisDate))
                .toList();

        if (targetSchedules.isEmpty()) {
            return List.of();
        }

        LocalDateTime searchStart = targetSchedules.stream()
                .map(schedule -> windowStartAt(schedule, analysisDate))
                .min(LocalDateTime::compareTo)
                .orElse(analysisDate.atStartOfDay());
        LocalDateTime searchEnd = targetSchedules.stream()
                .map(schedule -> nextScheduledAt(schedule, analysisDate))
                .max(LocalDateTime::compareTo)
                .orElse(analysisDate.plusDays(2).atStartOfDay());
        List<Long> scheduleIds = targetSchedules.stream()
                .map(MedicationSchedule::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<MedicationLog>> logsByScheduleId = medicationLogRepository
                .findByScheduleIdInAndTakenAtBetweenOrderByTakenAtAsc(scheduleIds, searchStart, searchEnd)
                .stream()
                .filter(log -> log.getScheduleId() != null)
                .collect(Collectors.groupingBy(MedicationLog::getScheduleId));

        return targetSchedules.stream()
                .map(schedule -> analyzeSchedule(schedule, analysisDate, now, logsByScheduleId))
                .sorted(Comparator
                        .comparing(MedicationAnalysisResult::wardId)
                        .thenComparing(MedicationAnalysisResult::scheduledAt)
                        .thenComparing(MedicationAnalysisResult::scheduleId))
                .toList();
    }

    private MedicationAnalysisResult analyzeSchedule(
            MedicationSchedule schedule,
            LocalDate analysisDate,
            LocalDateTime now,
            Map<Long, List<MedicationLog>> logsByScheduleId
    ) {
        LocalDateTime scheduledAt = LocalDateTime.of(analysisDate, schedule.getScheduledTime());
        LocalDateTime windowStartAt = windowStartAt(schedule, analysisDate);
        LocalDateTime deadlineAt = scheduledAt.plusMinutes(schedule.getAllowedDelayMinutes());
        Optional<MedicationLog> log = findMedicationLogForSchedule(
                schedule.getId(),
                windowStartAt,
                nextScheduledAt(schedule, analysisDate),
                logsByScheduleId
        );

        MedicationAnalysisStatus status = determineStatus(log.orElse(null), windowStartAt, deadlineAt, now);

        return new MedicationAnalysisResult(
                schedule.getWardId(),
                schedule.getId(),
                schedule.getMedicationName(),
                windowStartAt,
                scheduledAt,
                deadlineAt,
                log.map(MedicationLog::getTakenAt).orElse(null),
                status,
                schedule.getAllowedEarlyMinutes(),
                schedule.getAllowedDelayMinutes(),
                buildDetailMessage(status, schedule.getMedicationName())
        );
    }

    private Optional<MedicationLog> findMedicationLogForSchedule(
            Long scheduleId,
            LocalDateTime windowStartAt,
            LocalDateTime nextScheduledAt,
            Map<Long, List<MedicationLog>> logsByScheduleId
    ) {
        return logsByScheduleId.getOrDefault(scheduleId, List.of())
                .stream()
                .filter(log -> !log.getTakenAt().isBefore(windowStartAt))
                .filter(log -> log.getTakenAt().isBefore(nextScheduledAt))
                .findFirst();
    }

    private MedicationAnalysisStatus determineStatus(
            MedicationLog log,
            LocalDateTime windowStartAt,
            LocalDateTime deadlineAt,
            LocalDateTime now
    ) {
        if (log != null) {
            if (!log.getTakenAt().isBefore(windowStartAt) && !log.getTakenAt().isAfter(deadlineAt)) {
                return MedicationAnalysisStatus.ON_TIME;
            }
            return MedicationAnalysisStatus.DELAYED;
        }

        if (now.isAfter(deadlineAt)) {
            return MedicationAnalysisStatus.MISSED;
        }

        return MedicationAnalysisStatus.PENDING;
    }

    private boolean isTargetSchedule(MedicationSchedule schedule, LocalDate analysisDate) {
        if (schedule.getScheduleType() == MedicationScheduleType.DAILY) {
            return true;
        }

        return schedule.getScheduleType() == MedicationScheduleType.WEEKLY
                && schedule.getDayOfWeek() == analysisDate.getDayOfWeek();
    }

    private LocalDateTime nextScheduledAt(MedicationSchedule schedule, LocalDate analysisDate) {
        int daysToAdd = schedule.getScheduleType() == MedicationScheduleType.WEEKLY ? 7 : 1;
        return LocalDateTime.of(analysisDate.plusDays(daysToAdd), schedule.getScheduledTime());
    }

    private LocalDateTime windowStartAt(MedicationSchedule schedule, LocalDate analysisDate) {
        return LocalDateTime.of(analysisDate, schedule.getScheduledTime())
                .minusMinutes(schedule.getAllowedEarlyMinutes());
    }

    private String buildDetailMessage(MedicationAnalysisStatus status, String medicationName) {
        return switch (status) {
            case ON_TIME -> medicationName + " was recorded within the allowed medication window.";
            case DELAYED -> medicationName + " was recorded after the allowed medication deadline.";
            case MISSED -> medicationName + " has no medication log after the allowed deadline.";
            case PENDING -> medicationName + " is waiting for a medication log.";
        };
    }

    private void assertWardIsElder(Long wardId) {
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (ward.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.INVALID_ELDER);
        }
    }
}
