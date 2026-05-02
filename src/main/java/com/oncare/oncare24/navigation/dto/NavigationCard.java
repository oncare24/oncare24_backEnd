package com.oncare.oncare24.navigation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 길안내 한 단계.
 * <p>
 * 도보(walking)와 대중교통(transit) 응답에서 모두 같은 형식으로 사용.
 * 타입에 따라 활용되는 필드가 다름:
 *
 * <ul>
 *     <li><b>도보 카드 (WALK, STRAIGHT, TURN_*, CROSSWALK)</b>: distance, duration, path</li>
 *     <li><b>버스 카드 (BUS)</b>: duration, busNumber, busType, boardingStop, alightingStop, stationsCount, path</li>
 *     <li><b>지하철 카드 (SUBWAY)</b>: duration, lineNumber, lineColor, boardingStop, alightingStop, stationsCount, path</li>
 *     <li><b>출발/도착 카드 (START, ARRIVAL)</b>: instruction만</li>
 * </ul>
 *
 * 사용하지 않는 필드는 {@code @JsonInclude(NON_NULL)}로 응답에서 제외 → 깨끗한 JSON.
 *
 * @param path  실제 도로/대중교통 경로 좌표 리스트. {@code [[lon, lat], [lon, lat], ...]} 형태.
 *              TMAP 응답의 LineString 좌표가 그대로 담김. NaverMap 폴리라인 그릴 때 사용.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NavigationCard(
        NavigationCardType type,
        String instruction,
        Integer distance,
        Integer duration,

        // 버스 전용
        String busNumber,
        String busType,

        // 지하철 전용
        String lineNumber,
        String lineColor,

        // 대중교통 공통
        String boardingStop,
        String alightingStop,
        Integer stationsCount,

        // 실제 경로 좌표 [[lon, lat], ...] - NaverMap 폴리라인용
        List<List<Double>> path
) {

    /** 도보 카드 빌더 (직진/좌회전/우회전/횡단 등) */
    public static NavigationCard walk(NavigationCardType type, String instruction,
                                      int distance, int duration, List<List<Double>> path) {
        return new NavigationCard(type, instruction, distance, duration,
                null, null, null, null, null, null, null, path);
    }

    /** 출발 카드 */
    public static NavigationCard start(String instruction) {
        return new NavigationCard(NavigationCardType.START, instruction, 0, 0,
                null, null, null, null, null, null, null, null);
    }

    /** 도착 카드 */
    public static NavigationCard arrival(String instruction) {
        return new NavigationCard(NavigationCardType.ARRIVAL, instruction, 0, 0,
                null, null, null, null, null, null, null, null);
    }

    /** 버스 카드 */
    public static NavigationCard bus(String instruction, int duration,
                                     String busNumber, String busType,
                                     String boardingStop, String alightingStop, int stations,
                                     List<List<Double>> path) {
        return new NavigationCard(NavigationCardType.BUS, instruction, null, duration,
                busNumber, busType, null, null, boardingStop, alightingStop, stations, path);
    }

    /** 지하철 카드 */
    public static NavigationCard subway(String instruction, int duration,
                                        String lineNumber, String lineColor,
                                        String boardingStop, String alightingStop, int stations,
                                        List<List<Double>> path) {
        return new NavigationCard(NavigationCardType.SUBWAY, instruction, null, duration,
                null, null, lineNumber, lineColor, boardingStop, alightingStop, stations, path);
    }
}
