package com.oncare.oncare24.navigation.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.navigation.config.TmapProperties;
import com.oncare.oncare24.navigation.dto.NavigationCard;
import com.oncare.oncare24.navigation.dto.NavigationCardType;
import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;
import com.oncare.oncare24.navigation.dto.WalkingRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TMAP API 실제 호출 구현체.
 * <p>
 * <b>TMAP Pedestrian API</b>: GeoJSON FeatureCollection 응답.
 * features 배열의 각 LineString/Point를 순회하며 turnType, description으로 카드 생성.
 *
 * <p><b>TMAP Transit API</b>: itineraries 배열 응답.
 * 첫 itinerary의 legs 배열을 순회하며 mode(WALK/BUS/SUBWAY)별로 카드 생성.
 *
 * <p><b>응답 구조 차이</b>:
 * <ul>
 *     <li>Pedestrian: properties.turnType (코드), properties.description (안내 문구)</li>
 *     <li>Transit: legs[].mode, legs[].route, legs[].start, legs[].end</li>
 * </ul>
 *
 * 활성화: {@code tmap.mock=false}
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "tmap", name = "mock", havingValue = "false", matchIfMissing = false)
public class RealTmapClient implements TmapClient {

    private final TmapProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RealTmapClient(
            TmapProperties properties,
            @Qualifier("tmapRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public WalkingRouteResponse getWalkingRoute(RouteRequest request) {
        Map<String, Object> body = Map.of(
                "startX", request.startLon(),
                "startY", request.startLat(),
                "endX", request.endLon(),
                "endY", request.endLat(),
                "startName", "출발",
                "endName", request.endNameOrDefault(),
                "reqCoordType", "WGS84GEO",
                "resCoordType", "WGS84GEO"
        );

        try {
            String json = restClient.post()
                    .uri(properties.pedestrianUrl() + "?version=1")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseWalkingResponse(json, request.endNameOrDefault());

        } catch (RestClientException e) {
            log.warn("[TMAP] pedestrian API failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.NAVIGATION_FAILED);
        }
    }

    @Override
    public TransitRouteResponse getTransitRoute(RouteRequest request) {
        Map<String, Object> body = Map.of(
                "startX", request.startLon().toString(),
                "startY", request.startLat().toString(),
                "endX", request.endLon().toString(),
                "endY", request.endLat().toString(),
                "count", 1,                          // 첫 번째 추천 경로만
                "lang", 0,                           // 한국어
                "format", "json"
        );

        try {
            String json = restClient.post()
                    .uri(properties.transitUrl())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseTransitResponse(json, request.endNameOrDefault());

        } catch (RestClientException e) {
            log.warn("[TMAP] transit API failed: {}", e.getMessage());
            // 대중교통은 출발지/도착지가 너무 가까우면 "검색 결과 없음" 에러가 정상
            throw new CustomException(ErrorCode.NO_TRANSIT_ROUTE);
        }
    }

    // ==================== Pedestrian Parser ====================

    private WalkingRouteResponse parseWalkingResponse(String json, String endName) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode features = root.path("features");

            int totalDistance = 0;
            int totalTime = 0;
            List<NavigationCard> cards = new ArrayList<>();

            for (JsonNode feature : features) {
                JsonNode props = feature.path("properties");
                JsonNode geometry = feature.path("geometry");

                // 첫 Point (출발지)에 totalDistance/totalTime이 들어있음
                if (geometry.path("type").asText().equals("Point")
                        && props.has("totalDistance")) {
                    totalDistance = props.path("totalDistance").asInt();
                    totalTime = props.path("totalTime").asInt();
                }

                int turnType = props.path("turnType").asInt(-1);
                String desc = props.path("description").asText("");
                int dist = props.path("distance").asInt(0);
                int time = props.path("time").asInt(0);

                if (desc.isBlank()) continue;

                NavigationCardType type = mapTurnType(turnType);
                if (type == NavigationCardType.START) {
                    cards.add(NavigationCard.start(desc));
                } else if (type == NavigationCardType.ARRIVAL) {
                    cards.add(NavigationCard.arrival(endName + " 도착"));
                } else {
                    cards.add(NavigationCard.walk(type, desc, dist, time));
                }
            }

            return new WalkingRouteResponse(totalDistance, totalTime, cards);

        } catch (Exception e) {
            log.error("[TMAP] failed to parse pedestrian response", e);
            throw new CustomException(ErrorCode.NAVIGATION_FAILED);
        }
    }

    /**
     * TMAP turnType 코드를 우리 enum으로 매핑.
     * 주요 코드만 매핑, 나머지는 STRAIGHT로.
     * 참고: TMAP API 문서 - turnType 코드표
     */
    private NavigationCardType mapTurnType(int turnType) {
        return switch (turnType) {
            case 200 -> NavigationCardType.START;
            case 201 -> NavigationCardType.ARRIVAL;
            case 11 -> NavigationCardType.STRAIGHT;
            case 12 -> NavigationCardType.TURN_LEFT;
            case 13 -> NavigationCardType.TURN_RIGHT;
            case 14 -> NavigationCardType.TURN_BACK;
            case 125, 211, 212, 213, 214 -> NavigationCardType.CROSSWALK;
            default -> NavigationCardType.STRAIGHT;
        };
    }

    // ==================== Transit Parser ====================

    private TransitRouteResponse parseTransitResponse(String json, String endName) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // 응답 구조: metaData.plan.itineraries[0].legs[]
            JsonNode itinerary = root.path("metaData").path("plan").path("itineraries").path(0);
            if (itinerary.isMissingNode()) {
                throw new CustomException(ErrorCode.NO_TRANSIT_ROUTE);
            }

            int totalTime = itinerary.path("totalTime").asInt(0);
            int totalDistance = itinerary.path("totalDistance").asInt(0);
            int totalWalkTime = itinerary.path("totalWalkTime").asInt(0);
            int transferCount = itinerary.path("transferCount").asInt(0);
            Integer fare = itinerary.path("fare").path("regular").path("totalFare").isMissingNode()
                    ? null
                    : itinerary.path("fare").path("regular").path("totalFare").asInt();

            List<NavigationCard> cards = new ArrayList<>();
            cards.add(NavigationCard.start("출발지에서 출발"));

            for (JsonNode leg : itinerary.path("legs")) {
                String mode = leg.path("mode").asText("");
                int sectionTime = leg.path("sectionTime").asInt(0);
                int distance = leg.path("distance").asInt(0);
                String startName = leg.path("start").path("name").asText("");
                String endNameLeg = leg.path("end").path("name").asText("");

                switch (mode) {
                    case "WALK" -> {
                        String instruction = (endNameLeg.isBlank() ? "다음 정류장" : endNameLeg)
                                + "까지 도보 " + distance + "m";
                        cards.add(NavigationCard.walk(NavigationCardType.WALK, instruction, distance, sectionTime));
                    }
                    case "BUS" -> {
                        String busNumber = leg.path("route").asText("").replaceAll("^.*?:", ""); // "버스:120" → "120"
                        String busType = leg.path("type").asText("");
                        int stations = leg.path("stationCount").asInt(0);
                        String instruction = busNumber + "번 버스 탑승 (" + stations + "정거장)";
                        cards.add(NavigationCard.bus(instruction, sectionTime, busNumber, busType,
                                startName, endNameLeg, stations));
                    }
                    case "SUBWAY" -> {
                        String routeName = leg.path("route").asText("");
                        String lineNumber = routeName.replaceAll("[^0-9호선]", "");
                        int stations = leg.path("stationCount").asInt(0);
                        String instruction = routeName + " " + startName + " → " + endNameLeg
                                + " (" + stations + "정거장)";
                        cards.add(NavigationCard.subway(instruction, sectionTime, lineNumber, null,
                                startName, endNameLeg, stations));
                    }
                    default -> log.debug("[TMAP] unknown mode: {}", mode);
                }
            }

            cards.add(NavigationCard.arrival(endName + " 도착"));
            return new TransitRouteResponse(totalDistance, totalTime, totalWalkTime, fare, transferCount, cards);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TMAP] failed to parse transit response", e);
            throw new CustomException(ErrorCode.NAVIGATION_FAILED);
        }
    }
}
