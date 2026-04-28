package com.oncare.oncare24.navigation.service;

import com.oncare.oncare24.navigation.client.TmapClient;
import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;
import com.oncare.oncare24.navigation.dto.WalkingRouteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 길안내 서비스.
 * <p>
 * 현재는 TmapClient 호출을 단순 위임하지만, 향후:
 * <ul>
 *     <li>최근 길안내 이력 저장 → 자주 가는 병원 추천</li>
 *     <li>안전구역 출발/도착 보정 (집 좌표 자동 사용)</li>
 *     <li>음성 안내용 텍스트 정제</li>
 * </ul>
 * 같은 비즈니스 로직이 추가될 곳.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {

    private final TmapClient tmapClient;

    public WalkingRouteResponse getWalkingRoute(Long userId, RouteRequest request) {
        log.info("[Navigation] walking userId={}, ({},{}) → ({},{})",
                userId, request.startLat(), request.startLon(), request.endLat(), request.endLon());
        return tmapClient.getWalkingRoute(request);
    }

    public TransitRouteResponse getTransitRoute(Long userId, RouteRequest request) {
        log.info("[Navigation] transit userId={}, ({},{}) → ({},{})",
                userId, request.startLat(), request.startLon(), request.endLat(), request.endLon());
        return tmapClient.getTransitRoute(request);
    }
}
