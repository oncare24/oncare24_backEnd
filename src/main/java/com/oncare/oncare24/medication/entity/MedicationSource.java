package com.oncare.oncare24.medication.entity;

/**
 * 복약 일정의 출처(봉지 식별 단위 구분).
 * <ul>
 *   <li>{@code AUTO}   — CODEF 처방 자동 등록. groupId는 처방(codefKey) 단위.</li>
 *   <li>{@code MANUAL} — 사용자 수동 등록. groupId는 서버 발급 UUID(약 단위).</li>
 * </ul>
 */
public enum MedicationSource {
    AUTO,
    MANUAL
}
