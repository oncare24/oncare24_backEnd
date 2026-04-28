package com.oncare.oncare24.hospital.dto;

/**
 * 응급도 분류.
 * <p>
 * <ul>
 *     <li>{@code LOW} - 가벼운 증상. 일반 외래 진료로 충분.</li>
 *     <li>{@code MEDIUM} - 빠른 진료 필요. 같은 날 또는 24시간 내 외래 권장.</li>
 *     <li>{@code HIGH} - 응급. 응급실 또는 119 신고 필요.
 *         이 경우 서버는 일반 병원 대신 응급의료기관 리스트를 반환.</li>
 * </ul>
 *
 * LLM이 분류하며, 파싱 실패 시 안전 기본값으로 {@link #MEDIUM} 사용.
 */
public enum Urgency {
    LOW,
    MEDIUM,
    HIGH
}
