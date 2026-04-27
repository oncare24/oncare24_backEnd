package com.oncare.oncare24.auth.dto;

import com.oncare.oncare24.user.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청.
 * <p>
 * - phone: 하이픈 없는 숫자만 (예: 01012345678). 010/011/016/017/018/019 모두 허용.
 * - password: 최소 8자 이상.
 * - role: ELDER 또는 GUARDIAN.
 */
public record SignUpRequest(

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^01[0-9]\\d{7,8}$",
                message = "전화번호는 하이픈 없이 숫자만 입력해주세요. (예: 01012345678)"
        )
        String phone,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해주세요.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이내로 입력해주세요.")
        String name,

        @NotNull(message = "역할(ELDER/GUARDIAN)은 필수입니다.")
        UserRole role
) {
}
