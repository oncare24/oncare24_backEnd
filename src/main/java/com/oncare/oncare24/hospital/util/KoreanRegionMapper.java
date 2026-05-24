package com.oncare.oncare24.hospital.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 위경도 좌표를 한국 광역시·도 이름으로 매핑한다.
 * <p>각 시도의 위경도 경계 박스(bounding box)로 단순 판정. 경계 지역은 인접 시도와
 * 박스가 겹치므로 resolveAll()이 겹치는 시도를 모두 반환한다.
 */
public final class KoreanRegionMapper {

    private KoreanRegionMapper() {}

    /** 좌표 → 대표 시도명 (첫 매칭). 매칭 없으면 null. */
    public static String resolve(double lat, double lon) {
        List<String> all = resolveAll(lat, lon);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * 좌표가 포함되는 모든 시도명 (검사 순서 = 우선순위).
     * 경계 지역(예: 양산 사송 ↔ 부산)은 두 박스에 동시에 들어가므로 모두 반환해
     * 양쪽을 검색·병합한다. 한쪽만 쓰면 인접 시도의 가까운 병원이 누락됨.
     */
    public static List<String> resolveAll(double lat, double lon) {
        List<String> matched = new ArrayList<>();

        // 1. 광역시
        addIf(matched, in(lat, lon, 37.42, 37.70, 126.76, 127.18), "서울특별시");
        addIf(matched, in(lat, lon, 35.05, 35.40, 128.95, 129.30), "부산광역시");
        addIf(matched, in(lat, lon, 35.78, 36.00, 128.45, 128.78), "대구광역시");
        addIf(matched, in(lat, lon, 37.30, 37.60, 126.40, 126.78), "인천광역시");
        addIf(matched, in(lat, lon, 35.10, 35.27, 126.75, 126.98), "광주광역시");
        addIf(matched, in(lat, lon, 36.20, 36.50, 127.30, 127.55), "대전광역시");
        addIf(matched, in(lat, lon, 35.45, 35.70, 129.20, 129.40), "울산광역시");
        addIf(matched, in(lat, lon, 36.40, 36.80, 127.20, 127.40), "세종특별자치시");

        // 2. 제주
        addIf(matched, in(lat, lon, 33.10, 33.60, 126.10, 126.97), "제주특별자치도");

        // 3. 경상도
        addIf(matched, in(lat, lon, 35.55, 37.20, 128.00, 130.00), "경상북도");
        addIf(matched, in(lat, lon, 34.50, 35.95, 127.55, 129.20), "경상남도");

        // 4. 전라도
        addIf(matched, in(lat, lon, 35.40, 36.20, 126.30, 127.95), "전북특별자치도");
        addIf(matched, in(lat, lon, 33.80, 35.50, 125.80, 127.85), "전라남도");

        // 5. 충청도
        addIf(matched, in(lat, lon, 35.80, 37.10, 125.85, 127.50), "충청남도");
        addIf(matched, in(lat, lon, 36.00, 37.20, 127.30, 128.00), "충청북도");

        // 6. 경기/강원
        addIf(matched, in(lat, lon, 36.85, 38.30, 126.40, 127.85), "경기도");
        addIf(matched, in(lat, lon, 37.00, 38.62, 127.05, 129.40), "강원특별자치도");

        return matched;
    }

    private static void addIf(List<String> list, boolean cond, String value) {
        if (cond) list.add(value);
    }

    private static boolean in(double lat, double lon,
                              double minLat, double maxLat,
                              double minLon, double maxLon) {
        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }
}