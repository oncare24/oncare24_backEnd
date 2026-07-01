package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.event.MedicationAnalysisRefreshRequestedEvent;
import com.oncare.oncare24.analysis.service.EncryptedSourceEventService;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.dto.CreateMedicationGroupRequest;
import com.oncare.oncare24.medication.dto.MedicationGroupResponse;
import com.oncare.oncare24.medication.dto.MedicationPacketCreateRequest;
import com.oncare.oncare24.medication.dto.MedicationSchedulePayload;
import com.oncare.oncare24.medication.dto.MedicationScheduleSourceResponse;
import com.oncare.oncare24.medication.dto.UpdatePacketRequest;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.entity.MedicationSource;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 봉지(DoseGroup) 모델 변경 서비스 — 4장 4-3~4-6.
 * <p>
 * 모든 변경은 groupId 기준. 약명 등 본문은 복호화 원천({@link MedicationSourceQueryService})에서
 * 가져와 새 암호화 이벤트로 저장하므로, group_id 평문 백필 전 데이터(legacy:scheduleId)도 동작한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicationGroupCommandService {

    private final MedicationScheduleRepository medicationScheduleRepository;
    private final MedicationSourceQueryService sourceQueryService;
    private final MedicationGroupQueryService groupQueryService;
    private final EncryptedSourceEventService encryptedSourceEventService;
    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 4-3 봉지 시각 이동 — (groupId, fromTime)의 모든 성분 row를 한 번에 toTime으로. */
    @Transactional
    public void movePacketTime(Long currentUserId, Long wardId, String groupId,
                               LocalTime fromTime, LocalTime toTime) {
        List<MedicationScheduleSourceResponse> targets =
                packetRows(currentUserId, wardId, groupId, fromTime);

        for (MedicationScheduleSourceResponse t : targets) {
            MedicationSchedule s = getSchedule(t.scheduleId());
            s.updateScheduledTime(toTime);
            s.assignGroup(groupId, t.source());
            saveCreatedEvent(s, t.medicationName(), toTime,
                    t.allowedEarlyMinutes(), t.allowedDelayMinutes(),
                    t.scheduleType(), t.dayOfWeek(), t.daysOfWeek(),
                    t.startDate(), t.endDate(), groupId, t.source());
        }
        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(wardId));
        log.info("[MED-GROUP] movePacketTime ward={} group={} {}->{} ({}건)",
                wardId, groupId, fromTime, toTime, targets.size());
    }

    /** 4-4 봉지 속성(요일/기간) 변경 — 같은 (groupId, scheduledTime)을 새 요일 집합으로 재구성. */
    @Transactional
    public void updatePacket(Long currentUserId, Long wardId, String groupId,
                             LocalTime scheduledTime, UpdatePacketRequest request) {
        List<MedicationScheduleSourceResponse> targets =
                packetRows(currentUserId, wardId, groupId, scheduledTime);

        MedicationScheduleSourceResponse rep = targets.get(0);
        MedicationSource source = resolveSource(groupId, rep);

        List<DayOfWeek> targetDays = normalizeDays(request.scheduleType(), request.daysOfWeek());

        // 기존 요일별 row 인덱싱 (id 재사용). DAILY는 null 키.
        Map<DayOfWeek, MedicationScheduleSourceResponse> oldByDay = new HashMap<>();
        for (MedicationScheduleSourceResponse t : targets) {
            oldByDay.put(t.dayOfWeek(), t);
        }

        for (DayOfWeek dow : targetDays) {
            MedicationScheduleSourceResponse reuse = oldByDay.remove(dow);
            MedicationSchedule target = (reuse != null)
                    ? getSchedule(reuse.scheduleId())
                    : medicationScheduleRepository.save(MedicationSchedule.builder()
                    .wardId(wardId)
                    .scheduledTime(scheduledTime)
                    .endDate(request.endDate())
                    .groupId(groupId)
                    .source(source)
                    .build());

            target.updateScheduledTime(scheduledTime);
            target.updateEndDate(request.endDate());
            target.assignGroup(groupId, source);
            target.activate();

            saveCreatedEvent(target, rep.medicationName(), scheduledTime,
                    rep.allowedEarlyMinutes(), rep.allowedDelayMinutes(),
                    request.scheduleType(), dow, dow == null ? List.of() : List.of(dow),
                    request.startDate(), request.endDate(), groupId, source);
        }

        // 새 요일 집합에 없는 기존 요일은 비활성화
        for (MedicationScheduleSourceResponse leftover : oldByDay.values()) {
            deactivate(getSchedule(leftover.scheduleId()));
        }

        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(wardId));
        log.info("[MED-GROUP] updatePacket ward={} group={} time={} type={}",
                wardId, groupId, scheduledTime, request.scheduleType());
    }

    /** 4-5 수동 봉지(약) 생성 — 한 약이 한 group, packet별 요일 row 생성. */
    @Transactional
    public MedicationGroupResponse createGroup(Long currentUserId, Long wardId,
                                               CreateMedicationGroupRequest request) {
        assertCanAccessWard(currentUserId, wardId);

        String groupId = "manual:" + UUID.randomUUID();
        for (MedicationPacketCreateRequest packet : request.packets()) {
            List<DayOfWeek> days = normalizeDays(packet.scheduleType(), packet.daysOfWeek());
            for (DayOfWeek dow : days) {
                MedicationSchedule s = medicationScheduleRepository.save(MedicationSchedule.builder()
                        .wardId(wardId)
                        .scheduledTime(packet.scheduledTime())
                        .endDate(packet.endDate())
                        .groupId(groupId)
                        .source(MedicationSource.MANUAL)
                        .build());
                saveCreatedEvent(s, request.medicationName(), packet.scheduledTime(),
                        packet.allowedEarlyMinutes(), packet.allowedDelayMinutes(),
                        packet.scheduleType(), dow, dow == null ? List.of() : List.of(dow),
                        packet.startDate(), packet.endDate(), groupId, MedicationSource.MANUAL);
            }
        }
        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(wardId));
        log.info("[MED-GROUP] createGroup ward={} group={} name={} packets={}",
                wardId, groupId, request.medicationName(), request.packets().size());

        return groupQueryService.findGroups(currentUserId, wardId, false).groups().stream()
                .filter(g -> groupId.equals(g.groupId()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND));
    }

    /** 4-6 봉지(처방/약) 통째 삭제 — group의 모든 활성 일정 비활성화. */
    @Transactional
    public void deleteGroup(Long currentUserId, Long wardId, String groupId) {
        List<MedicationScheduleSourceResponse> targets =
                sourceQueryService.findMedicationSchedules(currentUserId, wardId, false).stream()
                        .filter(r -> Objects.equals(r.groupId(), groupId))
                        .toList();
        if (targets.isEmpty()) {
            throw new CustomException(ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND);
        }
        for (MedicationScheduleSourceResponse t : targets) {
            deactivate(getSchedule(t.scheduleId()));
        }
        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(wardId));
        log.info("[MED-GROUP] deleteGroup ward={} group={} ({}건)", wardId, groupId, targets.size());
    }

    /** 4-6 특정 봉지(시각)만 삭제. */
    @Transactional
    public void deletePacket(Long currentUserId, Long wardId, String groupId, LocalTime scheduledTime) {
        List<MedicationScheduleSourceResponse> targets =
                packetRows(currentUserId, wardId, groupId, scheduledTime);
        for (MedicationScheduleSourceResponse t : targets) {
            deactivate(getSchedule(t.scheduleId()));
        }
        eventPublisher.publishEvent(new MedicationAnalysisRefreshRequestedEvent(wardId));
        log.info("[MED-GROUP] deletePacket ward={} group={} time={} ({}건)",
                wardId, groupId, scheduledTime, targets.size());
    }

    // ── 내부 헬퍼 ──

    /** (groupId, scheduledTime)에 해당하는 활성 원천 row. 없으면 404. 권한 체크 포함(sourceQuery). */
    private List<MedicationScheduleSourceResponse> packetRows(
            Long currentUserId, Long wardId, String groupId, LocalTime scheduledTime) {
        List<MedicationScheduleSourceResponse> targets =
                sourceQueryService.findMedicationSchedules(currentUserId, wardId, false).stream()
                        .filter(r -> Objects.equals(r.groupId(), groupId)
                                && Objects.equals(r.scheduledTime(), scheduledTime))
                        .toList();
        if (targets.isEmpty()) {
            throw new CustomException(ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND);
        }
        return targets;
    }

    private MedicationSource resolveSource(String groupId, MedicationScheduleSourceResponse rep) {
        if (rep.source() != null) {
            return rep.source();
        }
        return (groupId != null && groupId.startsWith("codef:"))
                ? MedicationSource.AUTO : MedicationSource.MANUAL;
    }

    private void saveCreatedEvent(
            MedicationSchedule schedule, String medicationName, LocalTime scheduledTime,
            Integer allowedEarlyMinutes, Integer allowedDelayMinutes,
            MedicationScheduleType scheduleType, DayOfWeek dayOfWeek, List<DayOfWeek> daysOfWeek,
            java.time.LocalDate startDate, java.time.LocalDate endDate,
            String groupId, MedicationSource source) {
        MedicationSchedulePayload payload = new MedicationSchedulePayload(
                "CREATED", schedule.getId(), medicationName, scheduledTime,
                allowedEarlyMinutes != null ? allowedEarlyMinutes : 10,
                allowedDelayMinutes != null ? allowedDelayMinutes : 30,
                scheduleType, dayOfWeek, daysOfWeek, true, startDate, endDate, groupId, source);
        EncryptedActivityLog logEvent = saveScheduleEvent(schedule, payload);
        schedule.linkEncryptedActivityLog(logEvent.getId());
    }

    private void deactivate(MedicationSchedule schedule) {
        schedule.deactivate();
        MedicationSchedulePayload payload = new MedicationSchedulePayload(
                "DEACTIVATED", schedule.getId(), null, null, null, null, null, null, false);
        EncryptedActivityLog logEvent = saveScheduleEvent(schedule, payload);
        schedule.linkEncryptedActivityLog(logEvent.getId());
    }

    private EncryptedActivityLog saveScheduleEvent(MedicationSchedule schedule, MedicationSchedulePayload payload) {
        return encryptedSourceEventService.saveRequiredSourceEvent(
                schedule.getWardId(),
                ActivityEventType.MEDICATION_EVENT,
                "medication_schedule",
                schedule.getId(),
                LocalDateTime.now(),
                payload
        );
    }

    private MedicationSchedule getSchedule(Long scheduleId) {
        return medicationScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEDICATION_SCHEDULE_NOT_FOUND));
    }

    private List<DayOfWeek> normalizeDays(MedicationScheduleType scheduleType, List<DayOfWeek> daysOfWeek) {
        if (scheduleType == MedicationScheduleType.DAILY) {
            return Collections.singletonList(null);
        }
        LinkedHashSet<DayOfWeek> normalized = new LinkedHashSet<>();
        if (daysOfWeek != null) {
            normalized.addAll(daysOfWeek);
        }
        normalized.remove(null);
        if (normalized.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_MEDICATION_REQUEST,
                    "WEEKLY schedule requires daysOfWeek.");
        }
        return List.copyOf(normalized);
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
                currentUserId, wardId, GuardianWardStatus.ACCEPTED)) {
            return;
        }
        throw new CustomException(ErrorCode.MEDICATION_ACCESS_DENIED);
    }
}
