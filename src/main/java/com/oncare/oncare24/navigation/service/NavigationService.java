package com.oncare.oncare24.navigation.service;

import com.oncare.oncare24.navigation.client.TmapClient;
import com.oncare.oncare24.navigation.client.TransitClient;
import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;
import com.oncare.oncare24.navigation.dto.WalkingRouteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 길안내 오케스트레이터.
 * <p>
 * <b>멀티 프로바이더 전략</b>:
 * <ul>
 *     <li>도보: {@link TmapClient} - TMAP의 정확한 도로 매칭</li>
 *     <li>대중교통: {@link TransitClient} - ODsay의 풍부한 한국 노선 데이터</li>
 * </ul>
 * 도메인별 최적 API를 선택해 단일 API 의존을 피함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {

    private final TmapClient tmapClient;
    private final TransitClient transitClient;

    public WalkingRouteResponse getWalkingRoute(Long userId, RouteRequest request) {
        log.info("[Navigation] walking userId={}, ({},{}) → ({},{})",
                userId,
                request.startLat(), request.startLon(),
                request.endLat(), request.endLon());
        return tmapClient.getWalkingRoute(request);
    }

    public TransitRouteResponse getTransitRoute(Long userId, RouteRequest request) {
        log.info("[Navigation] transit userId={}, ({},{}) → ({},{})",
                userId,
                request.startLat(), request.startLon(),
                request.endLat(), request.endLon());
        return transitClient.getTransitRoute(request);
    }
}
