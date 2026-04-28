package com.oncare.oncare24.navigation.client;

import com.oncare.oncare24.location.util.Haversine;
import com.oncare.oncare24.navigation.dto.NavigationCard;
import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;
import com.oncare.oncare24.navigation.dto.WalkingRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TMAP AppKey 발급 전 개발용 Mock 구현체.
 * <p>
 * 실제 거리는 Haversine으로 계산하되, 카드 내용은 가상 정류장/직진 안내로 채움.
 * 흐름과 응답 구조 검증용.
 *
 * 활성화: {@code tmap.mock=true}
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "tmap", name = "mock", havingValue = "true", matchIfMissing = false)
public class MockTmapClient implements TmapClient {

    private static final double WALKING_SPEED_MPS = 1.2; // 도보 속도 (성인 평균)

    @Override
    public WalkingRouteResponse getWalkingRoute(RouteRequest request) {
        int distance = (int) Haversine.distance(
                request.startLat(), request.startLon(),
                request.endLat(), request.endLon());
        int duration = (int) (distance / WALKING_SPEED_MPS);

        log.info("[Mock TMAP] walking: distance={}m, duration={}s", distance, duration);

        // 단순 3단계: 출발 → 직진 → 도착
        List<NavigationCard> cards = List.of(
                NavigationCard.start("출발지에서 출발"),
                NavigationCard.walk(
                        com.oncare.oncare24.navigation.dto.NavigationCardType.STRAIGHT,
                        distance + "m 직진",
                        distance, duration
                ),
                NavigationCard.arrival(request.endNameOrDefault() + " 도착")
        );

        return new WalkingRouteResponse(distance, duration, cards);
    }

    @Override
    public TransitRouteResponse getTransitRoute(RouteRequest request) {
        int totalDistance = (int) Haversine.distance(
                request.startLat(), request.startLon(),
                request.endLat(), request.endLon());

        // Mock: 출발지 도보 200m + 버스 5정거장 + 하차지 도보 350m
        int walkStart = 200;
        int busDistance = totalDistance - walkStart - 350;
        int walkEnd = 350;

        int walkStartTime = (int) (walkStart / WALKING_SPEED_MPS);
        int busTime = 480; // Mock: 8분
        int walkEndTime = (int) (walkEnd / WALKING_SPEED_MPS);
        int totalTime = walkStartTime + busTime + walkEndTime;
        int totalWalkTime = walkStartTime + walkEndTime;

        log.info("[Mock TMAP] transit: distance={}m, time={}s", totalDistance, totalTime);

        List<NavigationCard> cards = List.of(
                NavigationCard.start("출발지에서 출발"),
                NavigationCard.walk(
                        com.oncare.oncare24.navigation.dto.NavigationCardType.WALK,
                        "테스트역까지 도보 " + walkStart + "m",
                        walkStart, walkStartTime
                ),
                NavigationCard.bus(
                        "120번 버스 탑승 (5정거장)",
                        busTime,
                        "120", "간선",
                        "테스트역", "병원입구",
                        5
                ),
                NavigationCard.walk(
                        com.oncare.oncare24.navigation.dto.NavigationCardType.WALK,
                        request.endNameOrDefault() + "까지 도보 " + walkEnd + "m",
                        walkEnd, walkEndTime
                ),
                NavigationCard.arrival(request.endNameOrDefault() + " 도착")
        );

        return new TransitRouteResponse(totalDistance, totalTime, totalWalkTime, 1500, 1, cards);
    }
}
