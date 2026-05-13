package com.oncare.oncare24.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        @Schema(description = "재발급에 사용할 Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9.refresh")
        String refreshToken
) {
}
