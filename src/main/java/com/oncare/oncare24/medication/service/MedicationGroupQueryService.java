package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.medication.dto.MedicationGroupListResponse;
import com.oncare.oncare24.medication.dto.MedicationGroupResponse;
import com.oncare.oncare24.medication.dto.MedicationItemResponse;
import com.oncare.oncare24.medication.dto.MedicationPacketResponse;
import com.oncare.oncare24.medication.dto.MedicationScheduleSourceResponse;
import com.oncare.oncare24.medication.dto.TodayMedicationItemResponse;
import com.oncare.oncare24.medication.dto.TodayMedicationResponse;
import com.oncare.oncare24.medication.dto.TodayMedicationSlotResponse;
import com.oncare.oncare24.medication.entity.MedicationLog;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.entity.MedicationSource;
import com.oncare.oncare24.medication.repository.MedicationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 봉지(DoseGroup) 모델 조회 서비스.
 * <p>
 * 복호화 원천({@link MedicationSourceQueryService})을 재사용해 groupId→packet(시각)→item(성분)
 * 계층으로 재구성한다(4-1). 오늘의 약(4-2)은 그날 유효한 일정 + 평문 복약 로그로 성분별 상태를 만든다.
 */
@Service
@RequiredArgsConstructor
public class MedicationGroupQueryService {

    private final MedicationSourceQueryService sourceQueryService;
    private final MedicationLogRepository medicationLogRepository;

    /** 4-1 봉지 계층 조회. */
    @Transactional(readOnly = true)
    public MedicationGroupListResponse findGroups(Long currentUserId, Long wardId, boolean includeInactive) {
        List<MedicationScheduleSourceResponse> rows =
                sourceQueryService.findMedicationSchedules(currentUserId, wardId, includeInactive);

        // groupId 단위로 묶기 (입력 정렬 순서 보존)
        LinkedHashMap<String, List<MedicationScheduleSourceResponse>> byGroup = new LinkedHashMap<>();
        for (MedicationScheduleSourceResponse r : rows) {
            byGroup.computeIfAbsent(r.groupId(), k -> new ArrayList<>()).add(r);
        }

        List<MedicationGroupResponse> groups = new ArrayList<>();
        for (Map.Entry<String, List<MedicationScheduleSourceResponse>> entry : byGroup.entrySet()) {
            String groupId = entry.getKey();
            List<MedicationScheduleSourceResponse> groupRows = entry.getValue();
            MedicationSource source = resolveSource(groupId, groupRows);
            // AUTO=봉지라 단일 약명 없음(null), MANUAL=약 단위라 약명 노출
            String medicationName = (source == MedicationSource.MANUAL)
                    ? groupRows.get(0).medicationName()
                    : null;

            groups.add(new MedicationGroupResponse(groupId, source, medicationName, toPackets(groupRows)));
        }
        return new MedicationGroupListResponse(groups);
    }

    /** 4-2 오늘의 약 (시각 슬롯 + 성분별 복용 상태). */
    @Transactional(readOnly = true)
    public TodayMedicationResponse findToday(Long currentUserId, Long wardId, LocalDate date) {
        List<MedicationScheduleSourceResponse> active =
                sourceQueryService.findMedicationSchedules(currentUserId, wardId, false);

        List<MedicationScheduleSourceResponse> due = active.stream()
                .filter(s -> isDueOn(s, date))
                .toList();

        if (due.isEmpty()) {
            return new TodayMedicationResponse(List.of());
        }

        // 그날 복용 로그(평문 schedule_id / taken_at) — 복호화 불필요
        List<Long> scheduleIds = due.stream()
                .map(MedicationScheduleSourceResponse::scheduleId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, MedicationLog> takenBySchedule = new HashMap<>();
        if (!scheduleIds.isEmpty()) {
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to = date.plusDays(1).atStartOfDay();
            for (MedicationLog log : medicationLogRepository
                    .findByScheduleIdInAndTakenAtBetweenOrderByTakenAtAsc(scheduleIds, from, to)) {
                takenBySchedule.putIfAbsent(log.getScheduleId(), log);  // 첫 복용 기록 채택
            }
        }

        // 시각(슬롯)별 그룹
        LinkedHashMap<LocalTime, List<MedicationScheduleSourceResponse>> byTime = new LinkedHashMap<>();
        for (MedicationScheduleSourceResponse s : due) {
            byTime.computeIfAbsent(s.scheduledTime(), k -> new ArrayList<>()).add(s);
        }

        List<TodayMedicationSlotResponse> slots = new ArrayList<>();
        for (Map.Entry<LocalTime, List<MedicationScheduleSourceResponse>> e : byTime.entrySet()) {
            LocalTime time = e.getKey();
            List<TodayMedicationItemResponse> items = e.getValue().stream()
                    .map(s -> {
                        MedicationLog log = takenBySchedule.get(s.scheduleId());
                        return new TodayMedicationItemResponse(
                                s.scheduleId(),
                                s.medicationName(),
                                log != null,
                                log != null ? log.getTakenAt() : null
                        );
                    })
                    .toList();
            boolean allTaken = !items.isEmpty()
                    && items.stream().allMatch(TodayMedicationItemResponse::taken);
            slots.add(new TodayMedicationSlotResponse(time, label(time), allTaken, items));
        }
        slots.sort(Comparator.comparing(
                TodayMedicationSlotResponse::scheduledTime,
                Comparator.nullsLast(LocalTime::compareTo)));
        return new TodayMedicationResponse(slots);
    }

    // ── 내부 헬퍼 ──

    private List<MedicationPacketResponse> toPackets(List<MedicationScheduleSourceResponse> groupRows) {
        // 시각 단위로 묶기
        LinkedHashMap<LocalTime, List<MedicationScheduleSourceResponse>> byTime = new LinkedHashMap<>();
        for (MedicationScheduleSourceResponse r : groupRows) {
            byTime.computeIfAbsent(r.scheduledTime(), k -> new ArrayList<>()).add(r);
        }

        List<MedicationPacketResponse> packets = new ArrayList<>();
        for (Map.Entry<LocalTime, List<MedicationScheduleSourceResponse>> e : byTime.entrySet()) {
            LocalTime time = e.getKey();
            List<MedicationScheduleSourceResponse> timeRows = e.getValue();
            MedicationScheduleSourceResponse rep = timeRows.get(0);

            // 같은 시각의 요일 합집합 (WEEKLY 요일별 row 통합)
            LinkedHashSet<DayOfWeek> days = new LinkedHashSet<>();
            for (MedicationScheduleSourceResponse r : timeRows) {
                if (r.daysOfWeek() != null) {
                    days.addAll(r.daysOfWeek());
                }
            }

            List<MedicationItemResponse> items = timeRows.stream()
                    .map(r -> new MedicationItemResponse(r.scheduleId(), r.medicationName()))
                    .toList();
            boolean active = timeRows.stream().anyMatch(MedicationScheduleSourceResponse::active);

            packets.add(new MedicationPacketResponse(
                    time,
                    label(time),
                    rep.scheduleType(),
                    List.copyOf(days),
                    rep.startDate(),
                    rep.endDate(),
                    active,
                    items
            ));
        }
        packets.sort(Comparator.comparing(
                MedicationPacketResponse::scheduledTime,
                Comparator.nullsLast(LocalTime::compareTo)));
        return packets;
    }

    private MedicationSource resolveSource(String groupId, List<MedicationScheduleSourceResponse> rows) {
        MedicationSource s = rows.get(0).source();
        if (s != null) {
            return s;
        }
        // 백필 전 데이터: groupId prefix로 추론
        if (groupId != null && groupId.startsWith("codef:")) {
            return MedicationSource.AUTO;
        }
        return MedicationSource.MANUAL;
    }

    private boolean isDueOn(MedicationScheduleSourceResponse s, LocalDate date) {
        if (s.startDate() != null && date.isBefore(s.startDate())) {
            return false;
        }
        if (s.endDate() != null && date.isAfter(s.endDate())) {
            return false;
        }
        if (s.scheduleType() == MedicationScheduleType.WEEKLY) {
            List<DayOfWeek> days = s.daysOfWeek();
            return days != null && days.contains(date.getDayOfWeek());
        }
        return true; // DAILY (또는 유형 미상) → 매일
    }

    /** 시각 기반 라벨. 정확한 구간이 없으면 시간대 추정. */
    private String label(LocalTime t) {
        if (t == null) {
            return null;
        }
        int h = t.getHour();
        if (h < 11) {
            return "아침약";
        }
        if (h < 15) {
            return "점심약";
        }
        if (h < 21) {
            return "저녁약";
        }
        return "자기전약";
    }
}
