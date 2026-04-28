package com.oncare.oncare24.navigation.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.navigation.config.OdsayProperties;
import com.oncare.oncare24.navigation.dto.NavigationCard;
import com.oncare.oncare24.navigation.dto.NavigationCardType;
import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * ODsay 대중교통 API 실제 호출 구현체.
 * <p>
 * 이번 버전 변경점: 응답 본문 일부 로깅 + info의 주요 필드 진단 출력.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "odsay", name = "mock", havingValue = "false", matchIfMissing = false)
public class RealOdsayClient implements TransitClient {

    private static final int TRAFFIC_TYPE_SUBWAY = 1;
    private static final int TRAFFIC_TYPE_BUS = 2;
    private static final int TRAFFIC_TYPE_WALK = 3;

    private final OdsayProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RealOdsayClient(
            OdsayProperties properties,
            @Qualifier("odsayRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public TransitRouteResponse getTransitRoute(RouteRequest request) {
        log.info("[ODsay] transit search: ({}, {}) → ({}, {})",
                request.startLat(), request.startLon(),
                request.endLat(), request.endLon());

        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/searchPubTransPathT")
                            .queryParam("apiKey", properties.apiKey())
                            .queryParam("SX", request.startLon())
                            .queryParam("SY", request.startLat())
                            .queryParam("EX", request.endLon())
                            .queryParam("EY", request.endLat())
                            .queryParam("OPT", 0)
                            .build())
                    .retrieve()
                    .body(String.class);

            return parseResponse(response, request);

        } catch (RestClientException e) {
            log.warn("[ODsay] API call failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.NAVIGATION_FAILED);
        }
    }

    private TransitRouteResponse parseResponse(String json, RouteRequest request) {
        try {
            // ★ 디버그: 응답 본문 일부 출력
            String preview = json != null && json.length() > 800 ? json.substring(0, 800) : json;
            log.info("[ODsay] response preview: {}", preview);

            JsonNode root = objectMapper.readTree(json);

            JsonNode error = root.get("error");
            if (error != null) {
                String code = error.path(0).path("code").asText("");
                String msg = error.path(0).path("message").asText("");
                log.warn("[ODsay] API error: code={}, msg={}", code, msg);
                throw new CustomException(ErrorCode.NO_TRANSIT_ROUTE);
            }

            JsonNode result = root.path("result");
            JsonNode paths = result.path("path");

            if (!paths.isArray() || paths.isEmpty()) {
                log.warn("[ODsay] no transit path found");
                throw new CustomException(ErrorCode.NO_TRANSIT_ROUTE);
            }

            JsonNode bestPath = paths.get(0);
            JsonNode info = bestPath.path("info");

            // ★ 디버그: 주요 필드 출력
            log.info("[ODsay] info: totalDistance={}, totalTime={}min, totalWalk={}m, payment={}",
                    info.path("totalDistance").asDouble(0),
                    info.path("totalTime").asInt(0),
                    info.path("totalWalk").asInt(0),
                    info.path("payment").asInt(0));

            int totalTime = info.path("totalTime").asInt(0) * 60;
            int totalDistance = (int) info.path("totalDistance").asDouble(0);  // double → int
            int totalWalk = info.path("totalWalk").asInt(0);
            int totalWalkTime = totalWalk > 0 ? (int) (totalWalk / 1.2) : 0;
            int payment = info.path("payment").asInt(0);
            int busTransitCount = info.path("busTransitCount").asInt(0);
            int subwayTransitCount = info.path("subwayTransitCount").asInt(0);
            int transferCount = busTransitCount + subwayTransitCount - 1;
            if (transferCount < 0) transferCount = 0;

            String endName = request.endNameOrDefault();
            List<NavigationCard> cards = new ArrayList<>();
            cards.add(NavigationCard.start("출발지에서 출발"));

            JsonNode subPaths = bestPath.path("subPath");
            for (int i = 0; i < subPaths.size(); i++) {
                JsonNode sub = subPaths.get(i);
                int trafficType = sub.path("trafficType").asInt(0);

                NavigationCard card = mapSubPath(sub, trafficType, i == subPaths.size() - 1, endName);
                if (card != null) {
                    cards.add(card);
                }
            }

            cards.add(NavigationCard.arrival(endName + " 도착"));

            log.info("[ODsay] parsed {} cards (totalDistance={}m, totalTime={}s, payment={}원, transfers={})",
                    cards.size(), totalDistance, totalTime, payment, transferCount);

            return new TransitRouteResponse(
                    totalDistance,
                    totalTime,
                    totalWalkTime,
                    payment > 0 ? payment : null,
                    transferCount,
                    cards
            );

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ODsay] failed to parse response", e);
            throw new CustomException(ErrorCode.NAVIGATION_FAILED);
        }
    }

    private NavigationCard mapSubPath(JsonNode sub, int trafficType, boolean isLast, String endName) {
        int distance = sub.path("distance").asInt(0);
        int sectionTime = sub.path("sectionTime").asInt(0) * 60;

        switch (trafficType) {
            case TRAFFIC_TYPE_WALK -> {
                String label = isLast ? endName + "까지" : "다음 정류장까지";
                String instruction = label + " 도보 " + distance + "m";
                List<List<Double>> path = extractWalkPath(sub);
                return NavigationCard.walk(
                        NavigationCardType.WALK, instruction, distance, sectionTime, path);
            }
            case TRAFFIC_TYPE_BUS -> {
                JsonNode lane = sub.path("lane").path(0);
                String busNoRaw = lane.path("busNo").asText("");
                String busNumber = extractBusNumber(busNoRaw);
                String busType = mapBusType(lane.path("type").asInt(0));
                String startName = sub.path("startName").asText("");
                String endNameStop = sub.path("endName").asText("");
                int stations = sub.path("stationCount").asInt(0);
                String instruction = busNumber + "번 버스 탑승 (" + stations + "정거장)";
                List<List<Double>> path = extractStationPath(sub);
                return NavigationCard.bus(
                        instruction, sectionTime, busNumber, busType,
                        startName, endNameStop, stations, path);
            }
            case TRAFFIC_TYPE_SUBWAY -> {
                JsonNode lane = sub.path("lane").path(0);
                String lineName = lane.path("name").asText("");
                String lineNumber = extractSubwayLine(lineName);
                String startName = sub.path("startName").asText("");
                String endNameStop = sub.path("endName").asText("");
                int stations = sub.path("stationCount").asInt(0);
                String instruction = lineName + " " + startName + " → " + endNameStop
                        + " (" + stations + "정거장)";
                List<List<Double>> path = extractStationPath(sub);
                return NavigationCard.subway(
                        instruction, sectionTime, lineNumber, null,
                        startName, endNameStop, stations, path);
            }
            default -> {
                log.debug("[ODsay] unknown trafficType: {}", trafficType);
                return null;
            }
        }
    }

    private List<List<Double>> extractWalkPath(JsonNode sub) {
        return new ArrayList<>();
    }

    private List<List<Double>> extractStationPath(JsonNode sub) {
        List<List<Double>> path = new ArrayList<>();
        JsonNode stations = sub.path("passStopList").path("stations");
        if (!stations.isArray()) return path;
        for (JsonNode station : stations) {
            try {
                double x = Double.parseDouble(station.path("x").asText("0"));
                double y = Double.parseDouble(station.path("y").asText("0"));
                if (x != 0 && y != 0) {
                    path.add(List.of(x, y));
                }
            } catch (NumberFormatException ignored) {}
        }
        return path;
    }

    private String extractBusNumber(String busNoRaw) {
        if (busNoRaw == null || busNoRaw.isBlank()) return "";
        int parenIdx = busNoRaw.indexOf('(');
        return parenIdx > 0 ? busNoRaw.substring(0, parenIdx).trim() : busNoRaw.trim();
    }

    private String mapBusType(int type) {
        return switch (type) {
            case 1 -> "간선";
            case 2 -> "좌석";
            case 3 -> "마을";
            case 4 -> "직행";
            case 5 -> "공항";
            default -> "일반";
        };
    }

    private String extractSubwayLine(String lineName) {
        if (lineName == null) return "";
        return lineName.replaceAll("[^0-9]", "").trim();
    }
}