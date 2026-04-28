package com.oncare.oncare24.location.util;

import java.math.BigDecimal;

/**
 * 두 GPS 좌표 사이 거리(미터)를 구하는 Haversine 공식.
 * <p>
 * 서버 지오펜싱 판정의 단일 진실 원천. 프론트에도 동일 구현이 있지만,
 * 클라이언트 불신뢰 원칙에 따라 서버는 독립적으로 거리 계산을 수행한다.
 *
 * <b>정확도</b>: WGS84 기준 ±0.5% 이내 (지오펜싱 반경 200~1000m 범위에서 ±5m 수준).
 * 더 정밀한 Vincenty 공식이 있으나 현 용도에는 과잉.
 */
public final class Haversine {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private Haversine() {
        // 유틸 클래스 — 인스턴스화 금지
    }

    /**
     * 두 좌표 사이 거리(미터). BigDecimal 입력은 DB 좌표(SafetyZone)와 직접 비교하기 위함.
     */
    public static double distance(
            BigDecimal lat1, BigDecimal lon1,
            BigDecimal lat2, BigDecimal lon2
    ) {
        return distance(
                lat1.doubleValue(), lon1.doubleValue(),
                lat2.doubleValue(), lon2.doubleValue()
        );
    }

    public static double distance(
            double lat1, double lon1,
            double lat2, double lon2
    ) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double centralAngle = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * centralAngle;
    }
}