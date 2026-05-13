package com.oncare.oncare24.auth.dto;

import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원가입 응답. 비밀번호는 절대 응답에 포함하지 않습니다.
 */
public record SignUpResponse(
        @Schema(description = "생성된 사용자 ID", example = "1")
        Long userId,
        @Schema(description = "회원 전화번호", example = "01012345678")
        String phone,
        @Schema(description = "회원 이름", example = "홍길동")
        String name,
        @Schema(description = "회원 역할", example = "GUARDIAN")
        UserRole role
) {
    public static SignUpResponse from(User user) {
        return new SignUpResponse(user.getId(), user.getPhone(), user.getName(), user.getRole());
    }
}
