package com.oncare.oncare24.user.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 엔티티.
 * <p>
 * - 로그인 식별자: {@code phone} (전화번호, 하이픈 제외 숫자만)
 * - 비밀번호: BCrypt 해시
 * - 역할: ELDER / GUARDIAN
 * - {@code phoneVerified}: 휴대전화 인증 완료 여부. 현재는 회원가입 시 false. 나중에 인증 API 추가되면 true로 변경.
 * - {@code email}, {@code fcmToken}: 옵션 컬럼. 회원 정보 수정/알림 연동 시 사용.
 */
@Entity
@Getter
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Builder
    private User(String phone, String password, String name, UserRole role, String email) {
        this.phone = phone;
        this.password = password;
        this.name = name;
        this.role = role;
        this.email = email;
        this.phoneVerified = false;
    }

    // === 비즈니스 메서드 ===

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    /**
     * 휴대전화 인증 완료 처리. 추후 SMS 인증 도메인이 추가될 때 사용됩니다.
     */
    public void verifyPhone() {
        this.phoneVerified = true;
    }
}
