package com.oncare.oncare24.auth.dto;

import com.oncare.oncare24.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청.
 * <p>
 * - phone: 하이픈 없는 숫자만 (예: 01012345678).
 * - password: 숫자 6자리 PIN (카카오뱅크/토스 표준, 시니어 진입장벽 완화).
 * - role: ELDER 또는 GUARDIAN.
 */
public record SignUpRequest(

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^01[0-9]\\d{7,8}$",
                message = "전화번호는 하이픈 없이 숫자만 입력해주세요. (예: 01012345678)"
        )
        @Schema(description = "회원 전화번호. 하이픈 없이 숫자만 입력합니다.", example = "01012345678")
        String phone,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^\\d{6}$",
                message = "비밀번호는 숫자 6자리로 입력해주세요."
        )
        @Schema(description = "회원 비밀번호", example = "123456")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이내로 입력해주세요.")
        @Schema(description = "회원 이름", example = "홍길동")
        String name,

        @NotNull(message = "역할(ELDER/GUARDIAN)은 필수입니다.")
        @Schema(description = "회원 역할. 피보호자는 ELDER, 보호자는 GUARDIAN을 사용합니다.", example = "GUARDIAN")
        UserRole role
) {
}
