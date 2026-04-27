package com.oncare.oncare24.auth.dto;

import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;

/**
 * 회원가입 응답. 비밀번호는 절대 응답에 포함하지 않습니다.
 */
public record SignUpResponse(
        Long userId,
        String phone,
        String name,
        UserRole role
) {
    public static SignUpResponse from(User user) {
        return new SignUpResponse(user.getId(), user.getPhone(), user.getName(), user.getRole());
    }
}
