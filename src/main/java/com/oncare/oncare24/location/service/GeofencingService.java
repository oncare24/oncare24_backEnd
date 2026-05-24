package com.oncare.oncare24.location.service;

import com.oncare.oncare24.location.entity.ZoneState;
import com.oncare.oncare24.location.entity.ZoneVisitState;
import com.oncare.oncare24.location.repository.ZoneVisitStateRepository;
import com.oncare.oncare24.location.util.Haversine;
import com.oncare.oncare24.notification.service.NotificationService;
import com.oncare.oncare24.safetyzone.entity.SafetyZone;
import com.oncare.oncare24.safetyzone.repository.SafetyZoneRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 공간 판정 도메인 서비스 (안심집합 / union 방식).
 * <p>
 * 등록된 모든 활성 안전구역을 하나의 "안심집합"으로 본다.
 * <ul>
 *     <li>어느 zone이든 하나라도 INSIDE → 안심집합 IN</li>
 *     <li>전부 OUTSIDE → 안심집합 OUT</li>
 * </ul>
 * 알림은 집합 상태가 전환되는 순간에만 1회:
 * <ul>
 *     <li>IN → OUT : 이탈 알림</li>
 *     <li>OUT → IN : 복귀 알림</li>
 *     <li>첫 보고(전부 UNKNOWN) : 알림 없이 상태만 기록 (신규 등록 중 외출 오알람 방지)</li>
 * </ul>
 * zone별 ZoneVisitState는 거리/방문시각 표시용으로 계속 갱신하되, 개별 zone 이탈은 알림 트리거로 쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeofencingService {

    private final SafetyZoneRepository safetyZoneRepository;
    private final ZoneVisitStateRepository zoneVisitStateRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /** 안심집합 상태. */
    private enum SetState { IN, OUT, UNDETERMINED }

    @Transactional
    public void evaluate(
            Long wardId,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime now
    ) {
        List<SafetyZone> zones = safetyZoneRepository
                .findByWardIdAndActiveTrueOrderByCreatedAtAsc(wardId);

        if (zones.isEmpty()) {
            return; // 등록된 안전구역 없음 — 판정할 것 없음
        }

        Map<Long, ZoneVisitState> stateMap = loadStateMap(wardId);

        // 1) 직전 안심집합 상태 — 이번 보고로 갱신하기 "전"의 zone별 상태로 계산
        SetState previousSet = aggregate(stateMap.values());

        // 2) 각 zone 거리 판정 + zone별 상태 갱신
        boolean anyInside = false;
        boolean anyNotificationEnabled = false;
        for (SafetyZone zone : zones) {
            double dist = Haversine.distance(
                    latitude, longitude,
                    zone.getLatitude(), zone.getLongitude()
            );
            ZoneState newState = dist <= zone.getRadius()
                    ? ZoneState.INSIDE
                    : ZoneState.OUTSIDE;

            ZoneVisitState visit = stateMap.computeIfAbsent(
                    zone.getId(),
                    zid -> zoneVisitStateRepository.save(
                            ZoneVisitState.builder()
                                    .wardId(wardId)
                                    .zoneId(zid)
                                    .now(now)
                                    .build()
                    )
            );
            visit.transitionTo(newState, now); // 반환값(개별 zone 전이)은 union 모드에서 미사용

            if (newState == ZoneState.INSIDE) anyInside = true;
            if (zone.isNotificationEnabled()) anyNotificationEnabled = true;
        }

        // 3) 현재 안심집합 상태
        SetState currentSet = anyInside ? SetState.IN : SetState.OUT;

        // 4) 집합 전환 시에만 알림 (이탈 알림이 모든 zone에서 꺼져 있으면 발송 안 함)
        if (!anyNotificationEnabled) {
            return;
        }

        if (previousSet == SetState.IN && currentSet == SetState.OUT) {
            notificationService.notifyZoneExit(wardId, resolveWardName(wardId));
            log.info("[GEOFENCE-SET-EXIT] ward={}", wardId);
        } else if (previousSet == SetState.OUT && currentSet == SetState.IN) {
            notificationService.notifyZoneEnter(wardId, resolveWardName(wardId));
            log.info("[GEOFENCE-SET-ENTER] ward={}", wardId);
        }
        // previousSet == UNDETERMINED : 첫 보고/전부 UNKNOWN → 알림 없이 상태만 기록
    }

    /**
     * zone별 상태들을 안심집합 상태로 집계.
     * INSIDE가 하나라도 있으면 IN, 없고 OUTSIDE가 하나라도 있으면 OUT,
     * 전부 UNKNOWN(또는 비어있음)이면 UNDETERMINED.
     */
    private SetState aggregate(Collection<ZoneVisitState> states) {
        boolean anyInside = false;
        boolean anyOutside = false;
        for (ZoneVisitState s : states) {
            if (s.getState() == ZoneState.INSIDE) anyInside = true;
            else if (s.getState() == ZoneState.OUTSIDE) anyOutside = true;
        }
        if (anyInside) return SetState.IN;
        if (anyOutside) return SetState.OUT;
        return SetState.UNDETERMINED;
    }

    private Map<Long, ZoneVisitState> loadStateMap(Long wardId) {
        Map<Long, ZoneVisitState> map = new HashMap<>();
        for (ZoneVisitState s : zoneVisitStateRepository.findByWardId(wardId)) {
            map.put(s.getZoneId(), s);
        }
        return map;
    }

    private String resolveWardName(Long wardId) {
        return userRepository.findById(wardId)
                .map(User::getName)
                .orElse("피보호자");
    }
}