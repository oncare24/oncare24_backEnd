package com.oncare.oncare24.location.entity;

/**
 * 안전구역별 출입 상태.
 * UNKNOWN: 첫 위치 보고 전 / zone 신규 등록 직후
 * INSIDE/OUTSIDE: 한 번이라도 위치 보고가 들어와 판정된 상태
 *
 * 이탈 알림은 INSIDE → OUTSIDE 전환 시점에만 1회 발송.
 * UNKNOWN → OUTSIDE 첫 진입은 알림 X (집을 나간 직후 안전구역 등록 시 오알람 방지).
 */
public enum ZoneState {
    UNKNOWN,
    INSIDE,
    OUTSIDE
}