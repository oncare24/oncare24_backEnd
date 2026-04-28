package com.oncare.oncare24.guardian.entity;

/**
 * 보호자-피보호자 연동 상태.
 * <p>
 * 흐름: 보호자가 SMS로 초대 발송 → PENDING → 피보호자 수락 시 ACCEPTED, 거절 시 REJECTED.
 * <p>
 * <b>안전구역/위치보고 등 권한 검증은 ACCEPTED 상태만 통과시킨다.</b>
 */
public enum GuardianWardStatus {
    PENDING,    // 초대 발송, 수락 대기
    ACCEPTED,   // 수락 — 권한 부여
    REJECTED    // 거절
}