package com.oncare.oncare24.navigation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

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
        List<List<Double>> path,

        // 도보 전용 — 어르신 안내용 랜드마크 (Tmap facilityName / nearPoiName). 없으면 null.
        String facilityName,
        String nearPoiName
) {

    /** 도보 카드 빌더 (직진/좌회전/우회전/횡단 등) */
    public static NavigationCard walk(NavigationCardType type, String instruction,
                                      int distance, int duration, List<List<Double>> path,
                                      String facilityName, String nearPoiName) {
        return new NavigationCard(type, instruction, distance, duration,
                null, null, null, null, null, null, null, path,
                emptyToNull(facilityName), emptyToNull(nearPoiName));
    }

    /** 출발 카드 */
    public static NavigationCard start(String instruction) {
        return new NavigationCard(NavigationCardType.START, instruction, 0, 0,
                null, null, null, null, null, null, null, null, null, null);
    }

    /** 출발 카드 (path 포함 버전) */
    public static NavigationCard start(String instruction, List<List<Double>> path) {
        return new NavigationCard(NavigationCardType.START, instruction, 0, 0,
                null, null, null, null, null, null, null, path, null, null);
    }

    /** 도착 카드 */
    public static NavigationCard arrival(String instruction) {
        return new NavigationCard(NavigationCardType.ARRIVAL, instruction, 0, 0,
                null, null, null, null, null, null, null, null, null, null);
    }

    /** 도착 카드 (path 포함 버전) */
    public static NavigationCard arrival(String instruction, List<List<Double>> path) {
        return new NavigationCard(NavigationCardType.ARRIVAL, instruction, 0, 0,
                null, null, null, null, null, null, null, path, null, null);
    }

    /** 버스 카드 */
    public static NavigationCard bus(String instruction, int duration,
                                     String busNumber, String busType,
                                     String boardingStop, String alightingStop, int stations,
                                     List<List<Double>> path) {
        return new NavigationCard(NavigationCardType.BUS, instruction, null, duration,
                busNumber, busType, null, null, boardingStop, alightingStop, stations, path,
                null, null);
    }

    /** 지하철 카드 */
    public static NavigationCard subway(String instruction, int duration,
                                        String lineNumber, String lineColor,
                                        String boardingStop, String alightingStop, int stations,
                                        List<List<Double>> path) {
        return new NavigationCard(NavigationCardType.SUBWAY, instruction, null, duration,
                null, null, lineNumber, lineColor, boardingStop, alightingStop, stations, path,
                null, null);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}