package com.oncare.oncare24.user.dto;

import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;

/**
 * 내 정보 조회 응답.
 * 비밀번호, FCM 토큰 같은 민감 정보는 절대 포함하지 않음.
 */
public record UserResponse(
        Long userId,
        String phone,
        String name,
        UserRole role,
        String email,
        boolean phoneVerified
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getName(),
                user.getRole(),
                user.getEmail(),
                user.isPhoneVerified()
        );
    }
}