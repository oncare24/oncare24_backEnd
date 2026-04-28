package com.oncare.oncare24.location.entity;

/**
 * 단말 상태 머신.
 * 회원가입 직후: NEVER_CONNECTED (한 번도 보고 안 옴)
 * 첫 보고 들어옴: ACTIVE
 * 30분 이상 미수신: DISCONNECTED (5분 배치가 전환)
 * DISCONNECTED → 보고 다시 들어오면 ACTIVE로 자동 복귀
 */
public enum DeviceState {
    NEVER_CONNECTED,
    ACTIVE,
    DISCONNECTED
}