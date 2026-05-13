package com.oncare.oncare24.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "전화번호는 필수입니다.")
        @Schema(description = "로그인 전화번호", example = "01012345678")
        String phone,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Schema(description = "로그인 비밀번호", example = "123456")
        String password
) {
}
