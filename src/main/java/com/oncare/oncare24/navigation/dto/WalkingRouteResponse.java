package com.oncare.oncare24.navigation.dto;

import java.util.List;

/**
 * 도보 길안내 응답.
 *
 * @param totalDistance  전체 거리(m)
 * @param totalTime      전체 예상 소요시간(초)
 * @param cards          단계별 안내 카드 리스트 (출발 → 안내들 → 도착)
 */
public record WalkingRouteResponse(
        int totalDistance,
        int totalTime,
        List<NavigationCard> cards
) {
}
