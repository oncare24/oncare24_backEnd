package com.oncare.oncare24.navigation.client;

import com.oncare.oncare24.location.util.Haversine;
import com.oncare.oncare24.navigation.dto.NavigationCard;
import com.oncare.oncare24.navigation.dto.NavigationCardType;
import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ODsay API 발급 전 / 발표 데모용 Mock 구현체.
 * <p>
 * 항상 동일한 형태의 버스 응답을 반환:
 * 출발 → 도보 → 버스 → 도보 → 도착 (5장 카드)
 *
 * <p>활성화: {@code odsay.mock=true}
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "odsay", name = "mock", havingValue = "true", matchIfMissing = false)
public class MockOdsayClient implements TransitClient {

    private static final double WALKING_SPEED_MPS = 1.2;

    @Override
    public TransitRouteResponse getTransitRoute(RouteRequest request) {
        int totalDistance = (int) Haversine.distance(
                request.startLat(), request.startLon(),
                request.endLat(), request.endLon());

        int walkStart = 200;
        int busDistance = Math.max(totalDistance - walkStart - 350, 500);
        int walkEnd = 350;

        int walkStartTime = (int) (walkStart / WALKING_SPEED_MPS);
        int busTime = 720;
        int walkEndTime = (int) (walkEnd / WALKING_SPEED_MPS);
        int totalTime = walkStartTime + busTime + walkEndTime;
        int totalWalkTime = walkStartTime + walkEndTime;

        log.info("[Mock ODsay] transit: distance={}m, time={}s", totalDistance, totalTime);

        // 단순 좌표 - 출발 → 1/3 → 2/3 → 도착
        double midLon1 = request.startLon() + (request.endLon() - request.startLon()) * 0.3;
        double midLat1 = request.startLat() + (request.endLat() - request.startLat()) * 0.3;
        double midLon2 = request.startLon() + (request.endLon() - request.startLon()) * 0.7;
        double midLat2 = request.startLat() + (request.endLat() - request.startLat()) * 0.7;

        List<List<Double>> busPath = List.of(
                List.of(midLon1, midLat1),
                List.of(midLon2, midLat2)
        );

        List<NavigationCard> cards = List.of(
                NavigationCard.start("출발지에서 출발"),
                NavigationCard.walk(
                        NavigationCardType.WALK,
                        "정류장까지 도보 " + walkStart + "m",
                        walkStart, walkStartTime, null, null, null
                ),
                NavigationCard.bus(
                        "120번 버스 탑승 (5정거장)",
                        busTime,
                        "120", "간선",
                        "출발정류장", "도착정류장",
                        5, busPath
                ),
                NavigationCard.walk(
                        NavigationCardType.WALK,
                        request.endNameOrDefault() + "까지 도보 " + walkEnd + "m",
                        walkEnd, walkEndTime, null, null, null
                ),
                NavigationCard.arrival(request.endNameOrDefault() + " 도착")
        );

        return new TransitRouteResponse(totalDistance, totalTime, totalWalkTime, 1500, 0, cards);
    }
}