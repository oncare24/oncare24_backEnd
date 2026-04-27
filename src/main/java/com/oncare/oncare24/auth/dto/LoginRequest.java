package com.oncare.oncare24.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "전화번호는 필수입니다.")
        String phone,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
