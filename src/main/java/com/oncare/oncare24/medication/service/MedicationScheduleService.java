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
import com.oncare.oncare24.notification.sender.FcmSender;
import com.oncare.oncare24.user.entity.User;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import com.oncare.oncare24.medication.dto.MedicationScheduleSourceResponse;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import com.oncare.oncare24.medication.dto.AutoRegisterResult;
import com.oncare.oncare24.medication.dto.PrescriptionImportItem;

@Service
@RequiredArgsConstructor
@Slf4j

public class MedicationScheduleService {

    private final MedicationScheduleRepository medicationScheduleRepository;
    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;
    private final EncryptedSourceEventService encryptedSourceEventService;
    private final ApplicationEventPublisher eventPublisher;
    private final MedicationSourceQueryService sourceQueryService;  // ← 추가
    private final FcmSender fcmSender;
    private final CodefKeyHasher codefKeyHasher;

    @Transactional
    // 복약 일정 생성과 암호화 이벤트 저장
    public MedicationScheduleResponse create(Long currentUserId, CreateMedicationScheduleRequest request) {
        log.info("[MED-CREATE] userId={} wardId={} name={} time={} type={}",
                currentUserId, request.wardId(), request.medicationName(),
                request.scheduledTime(), request.scheduleType());
        List<DayOfWeek> daysOfWeek = normalizeDaysOfWeek(request.scheduleType(), request.dayOfWeek(), request.daysOfWeek());
        validateAllowanceMinutes(request.allowedEarlyMinutes(), request.allowedDelayMinutes());
        assertCanAccessWard(currentUserId, request.wardId());

        List<MedicationSchedule> savedSchedules = new ArrayList<>();
        List<MedicationSchedulePayload> savedPayloads = new ArrayList<>();
        for (DayOfWeek dayOfWeek : daysOfWeek) {
            MedicationSchedule saved = medicationScheduleRepository.save(MedicationSchedule.builder()
                    .wardId(request.wardId())
                    .endDate(request.endDate())   // ← 추가
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
                    true,
                    request.startDate(),   // ← 추가
                    request.endDate()      // ← 추가
            );
            // 복약 일정 원천 데이터를 암호화 이벤트로 저장
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
        notifyWardScheduleChanged(currentUserId, request.wardId());
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
    // 복약 일정 수정과 암호화 이벤트 저장
    public MedicationScheduleResponse update(
            Long currentUserId,
            Long scheduleId,
            UpdateMedicationScheduleRequest request
    ) {
        log.info("[MED-UPDATE] userId={} scheduleId={} name={} time={} type={} active={}",
                currentUserId, scheduleId, request.medicationName(), request.scheduledTime(),
                request.scheduleType(), request.active());

        validateAllowanceMinutes(request.allowedEarlyMinutes(), request.allowedDelayMinutes());

        MedicationSchedule current = getScheduleOrThrow(scheduleId);
        assertCanAccessWard(currentUserId, current.getWardId());
        Long wardId = current.getWardId();

        // 1) 현재 schedule이 속한 그룹 식별 (수정 전 name+time+type 기준, active만)
        List<MedicationScheduleSourceResponse> allActive =
                sourceQueryService.findMedicationSchedules(currentUserId, wardId, false);

        MedicationScheduleSourceResponse currentSource = allActive.stream()
                .filter(s -> Objects.equals(s.scheduleId(), scheduleId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND));

        List<MedicationScheduleSourceResponse> groupSchedules = allActive.stream()
                .filter(s ->
                        Objects.equals(s.medicationName(), currentSource.medicationName())
                                && Objects.equals(s.scheduledTime(), currentSource.scheduledTime())
                                && s.scheduleType() == currentSource.scheduleType())
                .toList();

        // active=false 요청이면 그룹 전체 비활성화하고 끝
        if (!Boolean.TRUE.equals(request.active())) {
            for (MedicationScheduleSourceResponse old : groupSchedules) {
                deactivateScheduleById(old.scheduleId());
            }
            eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(wardId));
            notifyWardScheduleChanged(currentUserId, wardId);
            return MedicationScheduleResponse.from(current);
        }

        // 2) 새 요일 집합 (DAILY는 [null] 한 칸)
        List<DayOfWeek> targetDays = normalizeDaysOfWeek(
                request.scheduleType(), request.dayOfWeek(), request.daysOfWeek());

        // 3) 기존 그룹을 요일별로 인덱싱 (id 재사용용). DAILY는 null 키.
        Map<DayOfWeek, MedicationScheduleSourceResponse> oldByDay = new HashMap<>();
        for (MedicationScheduleSourceResponse old : groupSchedules) {
            oldByDay.put(old.dayOfWeek(), old);
        }

        List<MedicationSchedule> resultSchedules = new ArrayList<>();
        List<MedicationSchedulePayload> resultPayloads = new ArrayList<>();

        // 4) 요일별로: 기존에 있던 요일 → 같은 id 재사용(in-place), 새 요일 → 새로 생성
        for (DayOfWeek dow : targetDays) {
            MedicationScheduleSourceResponse reuse = oldByDay.remove(dow);
            MedicationSchedule target = (reuse != null)
                    ? getScheduleOrThrow(reuse.scheduleId())
                    : medicationScheduleRepository.save(MedicationSchedule.builder()
                    .wardId(wardId)
                    .endDate(request.endDate())   // ← 추가
                    .build());

            target.updateEndDate(request.endDate());      // ← 추가 (재사용 row의 기간 갱신)

            MedicationSchedulePayload payload = schedulePayload(
                    "CREATED",
                    target.getId(),
                    request.medicationName(),
                    request.scheduledTime(),
                    request.allowedEarlyMinutes(),
                    request.allowedDelayMinutes(),
                    request.scheduleType(),
                    dow,
                    dow == null ? List.of() : List.of(dow),
                    true,
                    request.startDate(),   // ← 추가
                    request.endDate()      // ← 추가
            );
            target.activate();
            // 수정된 복약 일정 원천 데이터를 암호화 이벤트로 저장
            EncryptedActivityLog logEvent = saveEncryptedScheduleEvent(target, payload);
            target.linkEncryptedActivityLog(logEvent.getId());
            resultSchedules.add(target);
            resultPayloads.add(payload);
        }

        // 5) 새 집합에 없는 기존 요일은 비활성화 (요일이 빠진 경우)
        for (MedicationScheduleSourceResponse leftover : oldByDay.values()) {
            deactivateScheduleById(leftover.scheduleId());
        }

        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(wardId));

        List<Long> resultIds = resultSchedules.stream().map(MedicationSchedule::getId).toList();
        List<DayOfWeek> responseDays = request.scheduleType() == MedicationScheduleType.DAILY
                ? List.of()
                : targetDays;

        notifyWardScheduleChanged(currentUserId, wardId);

        return MedicationScheduleResponse.from(
                resultSchedules.get(0), resultPayloads.get(0), resultIds, responseDays);
    }
    @Transactional
    // 복약 일정 비활성화 암호화 이벤트 저장
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
        // 비활성화된 복약 일정 상태를 암호화 이벤트로 저장
        EncryptedActivityLog encryptedLog = saveEncryptedScheduleEvent(schedule, payload);
        schedule.linkEncryptedActivityLog(encryptedLog.getId());
        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(schedule.getWardId()));
        notifyWardScheduleChanged(currentUserId, schedule.getWardId());
    }

    // 복약 일정 payload 암호화 저장
    private EncryptedActivityLog saveEncryptedScheduleEvent(MedicationSchedule schedule, MedicationSchedulePayload payload) {
        // occurredAt은 "이 이벤트가 발생한 시각" — 약 복용 예정 시각이 아니라 지금 시각이어야 한다.
        // payload.scheduledTime() 기준으로 잡으면 시간을 앞당기는 수정(오후→오전) 시 새 이벤트의
        // occurredAt이 옛 이벤트보다 빨라져서 source query의 정렬에서 옛 값이 덮어쓰는 버그 발생.
        // 복약 일정 payload를 encrypted_activity_log에 암호화 저장
        return encryptedSourceEventService.saveRequiredSourceEvent(
                schedule.getWardId(),
                ActivityEventType.MEDICATION_EVENT,
                "medication_schedule",
                schedule.getId(),
                LocalDateTime.now(),
                payload
        );
    }

    /**
     * 보호자가 어머니 schedule을 변경했을 때 어머니 폰에 silent push 발송.
     * 어머니 본인 변경 시엔 push 불필요 (본인 폰 캐시는 mutation onSuccess로 즉시 갱신).
     */
    private void notifyWardScheduleChanged(Long currentUserId, Long wardId) {
        if (currentUserId.equals(wardId)) return;

        User ward = userRepository.findById(wardId).orElse(null);
        if (ward == null || ward.getFcmToken() == null || ward.getFcmToken().isBlank()) {
            return;
        }

        fcmSender.sendDataOnly(
                ward.getFcmToken(),
                Map.of("type", "MEDICATION_SCHEDULE_CHANGED")
        );
        log.info("[MED-SCHEDULE-SYNC] notified wardId={} (changedBy={})", wardId, currentUserId);
    }

    // 기존 호출부(create/update/deactivate)는 그대로 — start/end 없이 호출 → null
    private MedicationSchedulePayload schedulePayload(
            String action, Long scheduleId, String medicationName, java.time.LocalTime scheduledTime,
            Integer allowedEarlyMinutes, Integer allowedDelayMinutes,
            MedicationScheduleType scheduleType, java.time.DayOfWeek dayOfWeek,
            List<DayOfWeek> daysOfWeek, boolean active
    ) {
        return schedulePayload(action, scheduleId, medicationName, scheduledTime,
                allowedEarlyMinutes, allowedDelayMinutes, scheduleType, dayOfWeek, daysOfWeek, active,
                null, null);
    }

    // 자동 등록(기간 약)이 쓰는 버전 — 시작일·종료일 포함
    private MedicationSchedulePayload schedulePayload(
            String action, Long scheduleId, String medicationName, java.time.LocalTime scheduledTime,
            Integer allowedEarlyMinutes, Integer allowedDelayMinutes,
            MedicationScheduleType scheduleType, java.time.DayOfWeek dayOfWeek,
            List<DayOfWeek> daysOfWeek, boolean active,
            LocalDate startDate, LocalDate endDate
    ) {
        return new MedicationSchedulePayload(
                action, scheduleId, medicationName, scheduledTime,
                allowedEarlyMinutes != null ? allowedEarlyMinutes : 10,
                allowedDelayMinutes != null ? allowedDelayMinutes : 30,
                scheduleType, dayOfWeek, daysOfWeek, active, startDate, endDate);
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

    // 복약 일정 내부 비활성화 암호화 저장
    private void deactivateScheduleById(Long scheduleId) {
        MedicationSchedule entity = getScheduleOrThrow(scheduleId);
        entity.deactivate();
        MedicationSchedulePayload payload = schedulePayload(
                "DEACTIVATED", entity.getId(),
                null, null, null, null, null, null,
                List.of(), false
        );
        // 비활성화된 복약 일정 상태를 암호화 이벤트로 저장
        EncryptedActivityLog logEvent = saveEncryptedScheduleEvent(entity, payload);
        entity.linkEncryptedActivityLog(logEvent.getId());
    }

    /**
     * CODEF 처방 목록을 본인(wardId) 복약 일정으로 자동 등록.
     * 정상 처방만 등록, 1일 N회는 기본 시각 N개 row로 펼침. 약 데이터는 암호화 경로로 저장.
     */
    @Transactional
    // 처방 기반 복약 일정 자동 등록과 암호화 저장
    public AutoRegisterResult autoRegisterFromPrescriptions(Long wardId, List<PrescriptionImportItem> items) {
        List<String> registered = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        if (items == null || items.isEmpty()) {
            return new AutoRegisterResult(registered, skipped, duplicates);
        }

        boolean anyCreated = false;

        for (PrescriptionImportItem item : items) {
            String name = item.drugName();

            Integer dailyDoses = parsePositiveInt(item.dailyDosesNumber());
            Integer totalDays = parsePositiveInt(item.totalDosingdays());
            LocalDate startDate = parseYmd(item.manufactureDate());
            if (dailyDoses == null || totalDays == null || startDate == null) {
                skipped.add(name);   // 자동 시간 배정에 필요한 값 부족 → 직접 등록 안내
                continue;
            }
            // ===== 데모 전용 (발표 후 제거): 조제일이 과거라 오늘 기준으로 당기되,
            //       처방번호 해시로 약마다 시작일을 0~6일 다르게 (전부 오늘 포함 유지) =====
            int offset = Math.floorMod(
                    (item.prescribeNo() == null ? name : item.prescribeNo()).hashCode(), 7);
            startDate = LocalDate.now().minusDays(6L - offset); // 오늘 -6 ~ 0일 사이에서 시작
            LocalDate endDate = startDate.plusDays(totalDays - 1L);
            if (endDate.isBefore(LocalDate.now().plusDays(1L))) {
                endDate = LocalDate.now().plusDays(2L); // 종료일이 너무 빠르면 최소 모레까지 보장
            }
            // (원래: startDate = parseYmd(item.manufactureDate()); endDate = startDate.plusDays(totalDays - 1L);)

            String codefKeyBidx = codefKeyHasher.hash(item.prescribeNo(), item.drugCode());
            if (codefKeyBidx != null
                    && medicationScheduleRepository.existsByWardIdAndCodefKeyBidx(wardId, codefKeyBidx)) {
                duplicates.add(name);   // 같은 처방-약 이미 등록됨
                continue;
            }

            for (LocalTime time : defaultTimes(dailyDoses)) {
                MedicationSchedule saved = medicationScheduleRepository.save(
                        MedicationSchedule.builder()
                                .wardId(wardId)
                                .endDate(endDate)
                                .codefKeyBidx(codefKeyBidx)
                                .build()
                );
                MedicationSchedulePayload payload = schedulePayload(
                        "CREATED",
                        saved.getId(),
                        name,
                        time,
                        null,   // allowedEarly → 기본 10
                        null,   // allowedDelay → 기본 30
                        MedicationScheduleType.DAILY,
                        null,
                        List.of(),
                        true,
                        startDate,   // ← 추가
                        endDate      // ← 추가
                );
                // CODEF 처방 복약 일정을 암호화 이벤트로 저장
                EncryptedActivityLog encryptedLog = saveEncryptedScheduleEvent(saved, payload);
                saved.linkEncryptedActivityLog(encryptedLog.getId());
            }
            registered.add(name);
            anyCreated = true;
        }

        if (anyCreated) {
            eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(wardId));
        }
        return new AutoRegisterResult(registered, skipped, duplicates);
    }

    /** 종료일 지난 활성 일정 비활성화. 알람을 멈추려면 암호화 DEACTIVATED 이벤트가 필요. */
    @Transactional
    // 만료 복약 일정 비활성화 암호화 저장
    public int deactivateExpiredSchedules(LocalDate today) {
        List<MedicationSchedule> expired =
                medicationScheduleRepository.findByActiveTrueAndEndDateBefore(today);
        int count = 0;
        for (MedicationSchedule schedule : expired) {
            try {
                schedule.deactivate();
                MedicationSchedulePayload payload = schedulePayload(
                        "DEACTIVATED", schedule.getId(),
                        null, null, null, null, null, null, List.of(), false
                );
                // 만료된 복약 일정 비활성화 이벤트를 암호화 저장
                EncryptedActivityLog logEvent = saveEncryptedScheduleEvent(schedule, payload);
                schedule.linkEncryptedActivityLog(logEvent.getId());
                eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(schedule.getWardId()));
                count++;
            } catch (Exception e) {
                // 한 건 실패(키 누락 등)가 배치 전체를 멈추지 않게 건너뜀
                log.warn("[MED-EXPIRE] skip scheduleId={} wardId={} reason={}",
                        schedule.getId(), schedule.getWardId(), e.getMessage());
            }
        }
        return count;
    }

    private List<LocalTime> defaultTimes(int dailyDoses) {
        return switch (dailyDoses) {
            case 1 -> List.of(LocalTime.of(8, 0));                                           // 아침 식후
            case 2 -> List.of(LocalTime.of(8, 0), LocalTime.of(19, 0));                      // 아침·저녁 식후
            case 3 -> List.of(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(19, 0)); // 아침·점심·저녁 식후
            case 4 -> List.of(LocalTime.of(8, 0), LocalTime.of(13, 0),
                    LocalTime.of(19, 0), LocalTime.of(22, 0));                     // + 취침 전
            default -> List.of(LocalTime.of(8, 0));
        };
    }

    private Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseYmd(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank()) return null;
        try {
            return LocalDate.parse(yyyymmdd.trim(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

}
