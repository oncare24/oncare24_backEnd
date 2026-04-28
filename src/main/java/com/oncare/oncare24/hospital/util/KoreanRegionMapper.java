package com.oncare.oncare24.hospital.util;

/**
 * 위경도 좌표를 한국 광역시·도 이름으로 매핑한다.
 * <p>
 * <b>구현 방식</b>: 각 시도의 위경도 경계 박스(bounding box)로 단순 판정.
 * 정밀한 행정구역 폴리곤이 아니므로 경계선 근처에선 인접 도가 잡힐 수 있지만,
 * 어차피 NMC API에 시도명을 넘긴 후 클라이언트 사이드에서 Haversine 거리 필터링을 거치므로
 * 사용자에게 잘못된 결과가 노출될 가능성은 거의 없다.
 *
 * <p><b>검사 순서가 중요</b>: 박스가 겹치는 영역에서는 먼저 매칭된 것이 우선.
 * <ol>
 *     <li>광역시 (작은 박스, 정확한 경계) - 가장 먼저</li>
 *     <li>경상도 (구미·안동 등 동쪽 내륙) - 충청도 박스와 겹치는 영역 우선 처리</li>
 *     <li>충청도 (서쪽 내륙)</li>
 *     <li>나머지 도</li>
 * </ol>
 * 예) 구미(36.139, 128.397)는 충청북도 박스에도 들어가고 경상북도 박스에도 들어가는데,
 * 경상북도를 먼저 검사하므로 정확히 매핑.
 *
 * <p><b>NMC API의 Q0 파라미터</b>는 "서울특별시", "경상북도" 등 한국어 표기를 받음.
 */
public final class KoreanRegionMapper {

    private KoreanRegionMapper() {}

    /**
     * 좌표 → 시도명. 매칭되는 시도가 없으면 null (한국 영토 밖이거나 경계 외부).
     */
    public static String resolve(double lat, double lon) {
        // ── 1. 광역시 (가장 작은 박스부터) ──
        if (in(lat, lon, 37.42, 37.70, 126.76, 127.18)) return "서울특별시";
        if (in(lat, lon, 35.05, 35.40, 128.95, 129.30)) return "부산광역시";
        if (in(lat, lon, 35.78, 36.00, 128.45, 128.78)) return "대구광역시";
        if (in(lat, lon, 37.30, 37.60, 126.40, 126.78)) return "인천광역시";
        if (in(lat, lon, 35.10, 35.27, 126.75, 126.98)) return "광주광역시";
        if (in(lat, lon, 36.20, 36.50, 127.30, 127.55)) return "대전광역시";
        if (in(lat, lon, 35.45, 35.70, 129.20, 129.40)) return "울산광역시";
        if (in(lat, lon, 36.40, 36.80, 127.20, 127.40)) return "세종특별자치시";

        // ── 2. 제주 ──
        if (in(lat, lon, 33.10, 33.60, 126.10, 126.97)) return "제주특별자치도";

        // ── 3. 경상도 (동쪽 내륙) - 충청도보다 먼저 검사 ──
        // 경상북도 (구미, 안동, 포항, 경주 포함)
        // 경상북도는 lon 128.0 이상부터 시작 (동쪽)
        if (in(lat, lon, 35.55, 37.20, 128.00, 130.00)) return "경상북도";
        // 경상남도 (창원, 진주 등) - 부산/울산 제외
        if (in(lat, lon, 34.50, 35.95, 127.55, 129.20)) return "경상남도";

        // ── 4. 전라도 ──
        // 전북특별자치도 (전주, 군산, 익산)
        if (in(lat, lon, 35.40, 36.20, 126.30, 127.95)) return "전북특별자치도";
        // 전라남도 (광주 제외, 여수 목포 순천)
        if (in(lat, lon, 33.80, 35.50, 125.80, 127.85)) return "전라남도";

        // ── 5. 충청도 (서쪽 내륙) ──
        // 충청남도 (천안, 아산, 공주)
        if (in(lat, lon, 35.80, 37.10, 125.85, 127.50)) return "충청남도";
        // 충청북도 (청주, 충주) - 경상도 제외 후 남는 영역
        if (in(lat, lon, 36.00, 37.20, 127.30, 128.00)) return "충청북도";

        // ── 6. 강원/경기 (북쪽) ──
        // 경기도 (수원, 성남 등) - 서울/인천 제외
        if (in(lat, lon, 36.85, 38.30, 126.40, 127.85)) return "경기도";
        // 강원특별자치도
        if (in(lat, lon, 37.00, 38.62, 127.05, 129.40)) return "강원특별자치도";

        // 매핑 실패
        return null;
    }

    private static boolean in(double lat, double lon,
                              double minLat, double maxLat,
                              double minLon, double maxLon) {
        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }
}