package com.oncare.oncare24.navigation.dto;

/**
 * 길안내 카드 타입.
 * <p>
 * 프론트가 카드 종류에 따라 다른 아이콘/색상을 그릴 때 사용한다.
 *
 * <ul>
 *     <li>{@code WALK} - 도보 구간</li>
 *     <li>{@code BUS} - 버스 탑승 구간</li>
 *     <li>{@code SUBWAY} - 지하철 탑승 구간</li>
 *     <li>{@code STRAIGHT} - 직진 (도보 단계별 안내)</li>
 *     <li>{@code TURN_LEFT} - 좌회전</li>
 *     <li>{@code TURN_RIGHT} - 우회전</li>
 *     <li>{@code TURN_BACK} - 유턴/뒤로</li>
 *     <li>{@code CROSSWALK} - 횡단보도 건너기</li>
 *     <li>{@code START} - 출발 카드 (첫 번째)</li>
 *     <li>{@code ARRIVAL} - 도착 카드 (마지막)</li>
 *     <li>{@code OTHER} - 그 외 (TMAP에서 알 수 없는 turnType)</li>
 * </ul>
 */
public enum NavigationCardType {
    WALK,
    BUS,
    SUBWAY,
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    TURN_BACK,
    CROSSWALK,
    START,
    ARRIVAL,
    OTHER
}
