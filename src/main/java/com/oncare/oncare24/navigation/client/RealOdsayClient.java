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
 *
 * <p><b>이번 진단 버전</b>: 폴리라인이 튀는 원인을 찾기 위해 좌표 단위로 자세한 로깅 추가.
 * <ul>
 *   <li>각 subPath의 trafficType, stationCount, startX/Y, endX/Y 출력</li>
 *   <li>각 정류장의 x/y 좌표 + 한국 영토 범위 검증 (lat 33-39, lon 124-132)</li>
 *   <li>범위 벗어난 좌표는 WARN 로그 + path에서 제외</li>
 *   <li>최종 카드별 path 사이즈 + 첫/마지막 좌표 출력</li>
 * </ul>
 *
 * <p>한 번 시연 후 로그를 보면 원인을 정확히 짚어낼 수 있다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "odsay", name = "mock", havingValue = "false", matchIfMissing = false)
public class RealOdsayClient implements TransitClient {

    private static final int TRAFFIC_TYPE_SUBWAY = 1;
    private static final int TRAFFIC_TYPE_BUS = 2;
    private static final int TRAFFIC_TYPE_WALK = 3;

    /** 한국 영토 범위 검증용 (좌표 튀기 감지). */
    private static final double KOREA_LAT_MIN = 33.0;
    private static final double KOREA_LAT_MAX = 39.0;
    private static final double KOREA_LON_MIN = 124.0;
    private static final double KOREA_LON_MAX = 132.0;

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

            log.info("[ODsay] info: totalDistance={}, totalTime={}min, totalWalk={}m, payment={}",
                    info.path("totalDistance").asDouble(0),
                    info.path("totalTime").asInt(0),
                    info.path("totalWalk").asInt(0),
                    info.path("payment").asInt(0));

            int totalTime = info.path("totalTime").asInt(0) * 60;
            int totalDistance = (int) info.path("totalDistance").asDouble(0);
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
            log.info("[ODsay] ▼▼▼ subPath 분석 시작 ({}개) ▼▼▼", subPaths.size());

            for (int i = 0; i < subPaths.size(); i++) {
                JsonNode sub = subPaths.get(i);
                int trafficType = sub.path("trafficType").asInt(0);

                // ★ 진단: subPath 전체 메타정보 출력
                logSubPathDiagnostics(i, sub, trafficType);

                NavigationCard card = mapSubPath(sub, trafficType, i == subPaths.size() - 1, endName);
                if (card != null) {
                    cards.add(card);
                    // ★ 진단: 생성된 카드의 path 정보 출력
                    logCardPath(i, card);
                }
            }

            cards.add(NavigationCard.arrival(endName + " 도착"));

            log.info("[ODsay] ▲▲▲ subPath 분석 완료 ▲▲▲");
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

    /**
     * ★ 진단 1: subPath의 메타정보 출력. trafficType, 거리, 시작/종료 좌표 등.
     * 좌표가 한국 영토 범위를 벗어나면 ⚠️ 경고.
     */
    private void logSubPathDiagnostics(int idx, JsonNode sub, int trafficType) {
        String typeName = switch (trafficType) {
            case TRAFFIC_TYPE_SUBWAY -> "SUBWAY";
            case TRAFFIC_TYPE_BUS -> "BUS";
            case TRAFFIC_TYPE_WALK -> "WALK";
            default -> "UNKNOWN(" + trafficType + ")";
        };

        int distance = sub.path("distance").asInt(0);
        int sectionTime = sub.path("sectionTime").asInt(0);
        int stationCount = sub.path("stationCount").asInt(0);
        String startName = sub.path("startName").asText("");
        String endName = sub.path("endName").asText("");
        double startX = sub.path("startX").asDouble(0);
        double startY = sub.path("startY").asDouble(0);
        double endX = sub.path("endX").asDouble(0);
        double endY = sub.path("endY").asDouble(0);

        log.info("[ODsay sub#{}] type={}, dist={}m, time={}min, stations={}, '{}' → '{}'",
                idx, typeName, distance, sectionTime, stationCount, startName, endName);
        log.info("[ODsay sub#{}] startXY=({}, {}) endXY=({}, {})",
                idx, startX, startY, endX, endY);

        // 좌표 검증 (x=lon, y=lat 가정)
        validateCoord("sub#" + idx + " start", startX, startY);
        validateCoord("sub#" + idx + " end", endX, endY);

        // BUS/SUBWAY인 경우 lane 정보 출력
        if (trafficType == TRAFFIC_TYPE_BUS || trafficType == TRAFFIC_TYPE_SUBWAY) {
            JsonNode lane = sub.path("lane").path(0);
            String laneName = lane.path("name").asText("");
            String busNo = lane.path("busNo").asText("");
            log.info("[ODsay sub#{}] lane: name='{}', busNo='{}'", idx, laneName, busNo);
        }
    }

    /**
     * ★ 진단 2: 카드의 path 사이즈 + 첫/마지막 좌표 출력.
     */
    private void logCardPath(int subIdx, NavigationCard card) {
        if (card.path() == null || card.path().isEmpty()) {
            log.info("[ODsay card sub#{}] type={}, path=EMPTY", subIdx, card.type());
            return;
        }
        List<Double> first = card.path().get(0);
        List<Double> last = card.path().get(card.path().size() - 1);
        log.info("[ODsay card sub#{}] type={}, path size={}, first=({}, {}), last=({}, {})",
                subIdx, card.type(), card.path().size(),
                first.size() >= 2 ? first.get(0) : "?",
                first.size() >= 2 ? first.get(1) : "?",
                last.size() >= 2 ? last.get(0) : "?",
                last.size() >= 2 ? last.get(1) : "?");
    }

    /**
     * 한국 영토 범위 좌표 검증. 벗어나면 WARN 로그.
     * x = 경도(lon), y = 위도(lat)로 가정.
     */
    private boolean validateCoord(String label, double x, double y) {
        boolean lonOk = x >= KOREA_LON_MIN && x <= KOREA_LON_MAX;
        boolean latOk = y >= KOREA_LAT_MIN && y <= KOREA_LAT_MAX;
        if (!lonOk || !latOk) {
            log.warn("[ODsay coord ⚠️] {} OUT OF KOREA: x={} (lon, expected 124-132), y={} (lat, expected 33-39)",
                    label, x, y);
            return false;
        }
        return true;
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
                List<List<Double>> path = extractStationPath(sub, "BUS");
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
                List<List<Double>> path = extractStationPath(sub, "SUBWAY");
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

    /**
     * passStopList의 정류장 좌표 추출 + 한국 영토 범위 검증.
     * 범위 벗어난 좌표는 path에 추가하지 않고 WARN 로그.
     *
     * <p><b>이 메소드가 폴리라인 튀는 원인을 잡아내는 핵심</b>:
     * 만약 어떤 정류장의 x가 132를 초과하거나 y가 33 미만이면 거기서 튐.
     */
    private List<List<Double>> extractStationPath(JsonNode sub, String segmentType) {
        List<List<Double>> path = new ArrayList<>();
        JsonNode stations = sub.path("passStopList").path("stations");
        if (!stations.isArray()) {
            log.warn("[ODsay {}] passStopList.stations is not an array", segmentType);
            return path;
        }

        int total = stations.size();
        int valid = 0;
        int invalid = 0;
        int missing = 0;

        for (int i = 0; i < total; i++) {
            JsonNode station = stations.get(i);
            String stationName = station.path("stationName").asText("?");
            try {
                double x = Double.parseDouble(station.path("x").asText("0"));
                double y = Double.parseDouble(station.path("y").asText("0"));

                if (x == 0 || y == 0) {
                    missing++;
                    log.warn("[ODsay {} stop#{}] '{}' MISSING coord: x={}, y={}",
                            segmentType, i, stationName, x, y);
                    continue;
                }

                // 한국 영토 범위 검증
                if (!validateCoord(segmentType + " stop#" + i + " '" + stationName + "'", x, y)) {
                    invalid++;
                    continue;  // 튀는 좌표는 path에 추가하지 않음
                }

                path.add(List.of(x, y));
                valid++;

                // 정류장 좌표 자세히 출력 (첫 3개와 마지막 1개만)
                if (i < 3 || i == total - 1) {
                    log.info("[ODsay {} stop#{}] '{}' = ({}, {})",
                            segmentType, i, stationName, x, y);
                }
            } catch (NumberFormatException e) {
                invalid++;
                log.warn("[ODsay {} stop#{}] '{}' INVALID coord: x='{}', y='{}'",
                        segmentType, i, stationName,
                        station.path("x").asText(""),
                        station.path("y").asText(""));
            }
        }

        log.info("[ODsay {}] stations: total={}, valid={}, invalid={}, missing={}",
                segmentType, total, valid, invalid, missing);
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