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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 공간 판정 도메인 서비스.
 * <p>
 * <b>알고리즘 (Android Geofencing API의 ENTER/EXIT 트랜지션 패턴 서버 구현)</b>
 * <ol>
 *     <li>대상 ward의 모든 활성 SafetyZone 로드</li>
 *     <li>각 zone마다 Haversine 거리 계산: distance ≤ radius 면 INSIDE, 초과면 OUTSIDE</li>
 *     <li>해당 (ward, zone) 짝의 ZoneVisitState를 찾아 상태 전이 시도</li>
 *     <li>전이 결과가 INSIDE → OUTSIDE 면 그 zone에 대해 이탈 알림 발행</li>
 *     <li>알림은 zone.notificationEnabled == true 일 때만 발행</li>
 * </ol>
 *
 * <b>왜 INSIDE → OUTSIDE만 알림인가</b>
 * <ul>
 *     <li>UNKNOWN → OUTSIDE: 첫 보고 또는 zone 신규 등록 직후. 외출 중에 zone을 만든 케이스라면 거짓 알람.</li>
 *     <li>OUTSIDE → INSIDE: "복귀"는 알림 가치 낮음. 보호자에게 노이즈.</li>
 *     <li>INSIDE → INSIDE / OUTSIDE → OUTSIDE: 변화 없음.</li>
 * </ul>
 *
 * <b>트랜잭션</b>: LocationReportService가 호출. 같은 트랜잭션 안에서 실행되도록
 * propagation 기본값(REQUIRED) 사용. 알림 발행 실패가 위치 보고 실패로 이어지진 않게 하려면
 * 추후 알림 부분만 @Async로 분리하는 것을 검토 (Step 10에서 결정).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeofencingService {

    private final SafetyZoneRepository safetyZoneRepository;
    private final ZoneVisitStateRepository zoneVisitStateRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * 위치 보고 1건에 대해 모든 활성 zone에 대한 지오펜싱 판정 수행.
     *
     * @param wardId   위치를 보고한 피보호자
     * @param latitude  보고된 위도
     * @param longitude 보고된 경도
     * @param now      판정 기준 시각 (LocationReportService에서 일관된 시각 전달)
     */
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

        // 기존 상태들을 zoneId 키로 한 번에 로드 (N+1 방지)
        Map<Long, ZoneVisitState> stateMap = loadStateMap(wardId);

        // ward 이름은 알림 발행 시 한 번만 조회 — 알림이 실제 발생할 때만 lazy하게
        String wardName = null;

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

            boolean shouldNotify = visit.transitionTo(newState, now);

            if (shouldNotify && zone.isNotificationEnabled()) {
                if (wardName == null) {
                    wardName = resolveWardName(wardId);
                }
                notificationService.notifyZoneExit(
                        wardId,
                        zone.getId(),
                        wardName,
                        zone.getName()
                );
                log.info("[GEOFENCE-EXIT] ward={}, zone={}({}), distance={}m",
                        wardId, zone.getId(), zone.getName(), Math.round(dist));
            }
        }
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