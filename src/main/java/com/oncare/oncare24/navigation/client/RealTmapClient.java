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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TMAP API 실제 호출 구현체.
 * <p>
 * <b>이번 버전 변경점</b>: 카드에 실제 도로 좌표(path)를 포함시켜
 * 프론트(NaverMap)가 정확한 도로/노선을 따라 폴리라인을 그릴 수 있도록 함.
 *
 * <p><b>도보 (Pedestrian)</b>: GeoJSON FeatureCollection 응답.
 * Point feature와 직후의 LineString feature가 짝을 이루어 카드 1장이 됨.
 * LineString.geometry.coordinates를 카드의 path에 담음.
 *
 * <p><b>대중교통 (Transit)</b>: itinerary.legs 배열 응답.
 * 각 leg에 passShape.linestring (또는 passList) 형태로 좌표가 들어있음.
 * "lon lat lon lat ..." 공백 구분 문자열을 파싱해 path에 담음.
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
                "count", 1,
                "lang", 0,
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

            // Point feature와 다음 LineString feature를 짝지어 카드 생성
            for (int i = 0; i < features.size(); i++) {
                JsonNode feature = features.get(i);
                JsonNode props = feature.path("properties");
                JsonNode geometry = feature.path("geometry");
                String geoType = geometry.path("type").asText();

                if (!"Point".equals(geoType)) continue;

                // totalDistance/totalTime은 첫 Point의 properties에 있음
                if (props.has("totalDistance")) {
                    totalDistance = props.path("totalDistance").asInt(0);
                    totalTime = props.path("totalTime").asInt(0);
                }

                int turnType = props.path("turnType").asInt(-1);
                String desc = props.path("description").asText("");

                // 다음 LineString feature 찾기 (있으면 path/distance/time 가져옴)
                List<List<Double>> path = null;
                int dist = 0;
                int time = 0;
                if (i + 1 < features.size()) {
                    JsonNode next = features.get(i + 1);
                    if ("LineString".equals(next.path("geometry").path("type").asText())) {
                        path = extractLineStringCoords(next.path("geometry").path("coordinates"));
                        dist = next.path("properties").path("distance").asInt(0);
                        time = next.path("properties").path("time").asInt(0);
                    }
                }

                if (desc.isBlank()) continue;

                NavigationCardType cardType = mapTurnType(turnType);
                if (cardType == NavigationCardType.START) {
                    cards.add(NavigationCard.start(desc));
                } else if (cardType == NavigationCardType.ARRIVAL) {
                    cards.add(NavigationCard.arrival(endName + " 도착"));
                } else {
                    cards.add(NavigationCard.walk(cardType, desc, dist, time, path));
                }
            }

            return new WalkingRouteResponse(totalDistance, totalTime, cards);

        } catch (Exception e) {
            log.error("[TMAP] failed to parse pedestrian response", e);
            throw new CustomException(ErrorCode.NAVIGATION_FAILED);
        }
    }

    /** GeoJSON LineString coordinates를 [[lon, lat], ...] 리스트로 변환. */
    private List<List<Double>> extractLineStringCoords(JsonNode coordsNode) {
        List<List<Double>> path = new ArrayList<>();
        if (coordsNode == null || !coordsNode.isArray()) return path;
        for (JsonNode pair : coordsNode) {
            if (pair.isArray() && pair.size() >= 2) {
                path.add(List.of(pair.get(0).asDouble(), pair.get(1).asDouble()));
            }
        }
        return path;
    }

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

                List<List<Double>> path = extractTransitLegPath(leg, mode);

                switch (mode) {
                    case "WALK" -> {
                        String instruction = (endNameLeg.isBlank() ? "다음 정류장" : endNameLeg)
                                + "까지 도보 " + distance + "m";
                        cards.add(NavigationCard.walk(
                                NavigationCardType.WALK, instruction, distance, sectionTime, path));
                    }
                    case "BUS" -> {
                        String busNumber = leg.path("route").asText("").replaceAll("^.*?:", "");
                        String busType = leg.path("type").asText("");
                        int stations = leg.path("stationCount").asInt(0);
                        String instruction = busNumber + "번 버스 탑승 (" + stations + "정거장)";
                        cards.add(NavigationCard.bus(instruction, sectionTime, busNumber, busType,
                                startName, endNameLeg, stations, path));
                    }
                    case "SUBWAY" -> {
                        String routeName = leg.path("route").asText("");
                        String lineNumber = routeName.replaceAll("[^0-9호선]", "");
                        int stations = leg.path("stationCount").asInt(0);
                        String instruction = routeName + " " + startName + " → " + endNameLeg
                                + " (" + stations + "정거장)";
                        cards.add(NavigationCard.subway(instruction, sectionTime, lineNumber, null,
                                startName, endNameLeg, stations, path));
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

    /**
     * Transit leg에서 좌표 리스트 추출.
     *
     * <p>Transit 응답의 좌표는 두 가지 형태로 올 수 있음:
     * <ul>
     *     <li>WALK leg: steps[].linestring에 "lon,lat lon,lat ..." (공백 구분, 컴마 분리)</li>
     *     <li>BUS/SUBWAY leg: passShape.linestring에 "lon,lat lon,lat ..." (같은 형태)</li>
     * </ul>
     */
    private List<List<Double>> extractTransitLegPath(JsonNode leg, String mode) {
        List<List<Double>> path = new ArrayList<>();
        try {
            String linestring = null;
            if ("WALK".equals(mode)) {
                // WALK leg는 steps 안에 여러 linestring이 있을 수 있음
                JsonNode steps = leg.path("steps");
                if (steps.isArray()) {
                    for (JsonNode step : steps) {
                        String ls = step.path("linestring").asText("");
                        appendLinestring(path, ls);
                    }
                    return path;
                }
                linestring = leg.path("linestring").asText("");
            } else {
                // BUS/SUBWAY leg는 passShape.linestring
                linestring = leg.path("passShape").path("linestring").asText("");
            }
            appendLinestring(path, linestring);
        } catch (Exception e) {
            log.debug("[TMAP] failed to extract transit path: {}", e.getMessage());
        }
        return path;
    }

    /** "lon,lat lon,lat ..." 형태의 문자열을 path 리스트에 append. */
    private void appendLinestring(List<List<Double>> path, String linestring) {
        if (linestring == null || linestring.isBlank()) return;
        String[] points = linestring.trim().split("\\s+");
        for (String point : points) {
            String[] xy = point.split(",");
            if (xy.length >= 2) {
                try {
                    double lon = Double.parseDouble(xy[0]);
                    double lat = Double.parseDouble(xy[1]);
                    path.add(List.of(lon, lat));
                } catch (NumberFormatException ignored) {}
            }
        }
    }
}
