package com.oncare.oncare24.navigation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 대중교통 길안내 응답.
 *
 * @param totalDistance   전체 거리(m)
 * @param totalTime       전체 예상 소요시간(초). 도보 + 탑승 + 환승 포함
 * @param totalWalkTime   도보 구간 합계 시간(초)
 * @param totalFare       총 요금(원). 정보 없으면 null
 * @param transferCount   환승 횟수
 * @param cards           단계별 안내 카드 (출발 → 도보 → 탑승 → 도보 → ... → 도착)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransitRouteResponse(
        int totalDistance,
        int totalTime,
        int totalWalkTime,
        Integer totalFare,
        int transferCount,
        List<NavigationCard> cards
) {
}
