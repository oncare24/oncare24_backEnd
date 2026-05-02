package com.oncare.oncare24.navigation.client;

import com.oncare.oncare24.location.util.Haversine;
import com.oncare.oncare24.navigation.dto.NavigationCard;
import com.oncare.oncare24.navigation.dto.NavigationCardType;
import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;
import com.oncare.oncare24.navigation.dto.WalkingRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TMAP AppKey 발급 전 개발용 Mock 구현체.
 * 흐름과 응답 구조 검증용.
 *
 * 활성화: {@code tmap.mock=true}
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "tmap", name = "mock", havingValue = "true", matchIfMissing = false)
public class MockTmapClient implements TmapClient {

    private static final double WALKING_SPEED_MPS = 1.2;

    @Override
    public WalkingRouteResponse getWalkingRoute(RouteRequest request) {
        int distance = (int) Haversine.distance(
                request.startLat(), request.startLon(),
                request.endLat(), request.endLon());
        int duration = (int) (distance / WALKING_SPEED_MPS);

        log.info("[Mock TMAP] walking: distance={}m, duration={}s", distance, duration);

        // Mock도 직선 path 만들어서 채워둠 (어댑터가 잘 작동하는지 검증용)
        List<List<Double>> path = List.of(
                List.of(request.startLon(), request.startLat()),
                List.of(request.endLon(), request.endLat())
        );

        List<NavigationCard> cards = List.of(
                NavigationCard.start("출발지에서 출발"),
                NavigationCard.walk(
                        NavigationCardType.STRAIGHT,
                        distance + "m 직진",
                        distance, duration, path
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

        int walkStart = 200;
        int busDistance = totalDistance - walkStart - 350;
        int walkEnd = 350;

        int walkStartTime = (int) (walkStart / WALKING_SPEED_MPS);
        int busTime = 480;
        int walkEndTime = (int) (walkEnd / WALKING_SPEED_MPS);
        int totalTime = walkStartTime + busTime + walkEndTime;
        int totalWalkTime = walkStartTime + walkEndTime;

        log.info("[Mock TMAP] transit: distance={}m, time={}s", totalDistance, totalTime);

        // Mock의 단순 좌표 (출발 → 1/3 지점 → 2/3 지점 → 도착)
        double midLon1 = request.startLon() + (request.endLon() - request.startLon()) * 0.3;
        double midLat1 = request.startLat() + (request.endLat() - request.startLat()) * 0.3;
        double midLon2 = request.startLon() + (request.endLon() - request.startLon()) * 0.7;
        double midLat2 = request.startLat() + (request.endLat() - request.startLat()) * 0.7;

        List<List<Double>> walkStartPath = List.of(
                List.of(request.startLon(), request.startLat()),
                List.of(midLon1, midLat1)
        );
        List<List<Double>> busPath = List.of(
                List.of(midLon1, midLat1),
                List.of(midLon2, midLat2)
        );
        List<List<Double>> walkEndPath = List.of(
                List.of(midLon2, midLat2),
                List.of(request.endLon(), request.endLat())
        );

        List<NavigationCard> cards = List.of(
                NavigationCard.start("출발지에서 출발"),
                NavigationCard.walk(
                        NavigationCardType.WALK,
                        "테스트역까지 도보 " + walkStart + "m",
                        walkStart, walkStartTime, walkStartPath
                ),
                NavigationCard.bus(
                        "120번 버스 탑승 (5정거장)",
                        busTime,
                        "120", "간선",
                        "테스트역", "병원입구",
                        5, busPath
                ),
                NavigationCard.walk(
                        NavigationCardType.WALK,
                        request.endNameOrDefault() + "까지 도보 " + walkEnd + "m",
                        walkEnd, walkEndTime, walkEndPath
                ),
                NavigationCard.arrival(request.endNameOrDefault() + " 도착")
        );

        return new TransitRouteResponse(totalDistance, totalTime, totalWalkTime, 1500, 1, cards);
    }
}
