package com.oncare.oncare24.user.entity;

/**
 * 사용자 역할.
 * <p>
 * 설계명세서 기준:
 * <ul>
 *     <li>{@code ELDER}     - 고령자 (피보호자). 본인의 건강/안전을 관리받는 사용자</li>
 *     <li>{@code GUARDIAN}  - 보호자. ELDER와 연동되어 원격으로 모니터링하는 사용자</li>
 * </ul>
 */
public enum UserRole {
    ELDER,
    GUARDIAN
}
