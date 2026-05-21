// back/src/main/java/com/oncare/oncare24/user/entity/User.java
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

    /** 만 나이. ELDER 필수, GUARDIAN nullable. Graph RAG ELDERLY 판정용. */
    @Column(name = "age")
    private Integer age;

    /** 임신 여부. ELDER 필수, GUARDIAN nullable. Graph RAG PREGNANCY 판정용. */
    @Column(name = "is_pregnant")
    private Boolean isPregnant;

    @Builder
    private User(String phone, String password, String name, UserRole role, String email,
                 Integer age, Boolean isPregnant) {
        this.phone = phone;
        this.password = password;
        this.name = name;
        this.role = role;
        this.email = email;
        this.age = age;
        this.isPregnant = isPregnant;
        this.phoneVerified = false;
    }

    // === 비즈니스 메서드 ===

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void verifyPhone() {
        this.phoneVerified = true;
    }
}