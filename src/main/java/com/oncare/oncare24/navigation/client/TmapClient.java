package com.oncare.oncare24.navigation.client;

import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;
import com.oncare.oncare24.navigation.dto.WalkingRouteResponse;

/**
 * TMAP 길안내 API 추상화.
 * <p>
 * 두 구현체:
 * <ul>
 *     <li>{@link MockTmapClient} - AppKey 발급 전 / 오프라인 개발용. 가짜 카드 반환.</li>
 *     <li>{@link RealTmapClient} - 실제 TMAP API 호출.</li>
 * </ul>
 * application.yml의 {@code tmap.mock} 값에 따라 둘 중 하나만 활성화.
 */
public interface TmapClient {

    /**
     * 도보 경로 안내 (TMAP Pedestrian API).
     * <p>
     * 보통 1~3km 이내 짧은 거리에 사용.
     */
    WalkingRouteResponse getWalkingRoute(RouteRequest request);

    /**
     * 대중교통 경로 안내 (TMAP Transit API).
     * <p>
     * 출발지 도보 → 버스/지하철 → 환승 도보 → ... → 도착지 도보 까지 한 응답에 포함.
     */
    TransitRouteResponse getTransitRoute(RouteRequest request);
}
