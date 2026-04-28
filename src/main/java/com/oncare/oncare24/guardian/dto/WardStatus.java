package com.oncare.oncare24.guardian.dto;

/**
 * 보호자 홈에 표시될 피보호자 상태.
 * <p>
 * 안전구역 출입 상태(ZoneState)와는 다른 차원 — 단말 연결성까지 포함한 종합 판정.
 */
public enum WardStatus {
    /** 어느 안전구역이든 INSIDE — 가장 안심. */
    INSIDE,
    /** 어떤 활성 안전구역에서도 INSIDE가 아니고, 적어도 한 곳은 OUTSIDE. */
    OUTSIDE,
    /** 30분간 위치 보고 끊김. */
    DISCONNECTED,
    /** 단말 미연결, 안전구역 미설정, 또는 첫 보고 전 — 표시할 게 없는 상태. */
    UNKNOWN
}