package com.oncare.oncare24.sos.entity;

/**
 * SOS 호출 시 위치 정보의 출처.
 * <p>
 * 사후 분석 시 "이 호출의 위치가 얼마나 신뢰할 만한지" 판단 근거.
 */
public enum SosLocationSource {
    /** 호출 요청 본문에 좌표가 포함됨 (가장 신뢰도 높음 — 호출 시점 실시간 GPS). */
    CLIENT,
    /** 요청에 좌표 없어서 location_reports 최신 row로 폴백. 최대 30분 전 위치일 수 있음. */
    FALLBACK,
    /** 요청에도, location_reports에도 없음. 알림 본문에 위치 미포함. */
    NONE
}