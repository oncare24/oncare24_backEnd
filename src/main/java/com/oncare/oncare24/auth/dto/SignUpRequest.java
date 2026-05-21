// back/src/main/java/com/oncare/oncare24/auth/dto/SignUpRequest.java
package com.oncare.oncare24.auth.dto;

import com.oncare.oncare24.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        UserRole role,

        /**
         * 만 나이. ELDER 회원은 필수, GUARDIAN 은 null 허용.
         * (ELDER 필수 검증은 AuthService 에서 role 과 함께 처리)
         */
        @Min(value = 1, message = "나이는 1세 이상이어야 합니다.")
        @Max(value = 120, message = "나이는 120세 이하여야 합니다.")
        @Schema(description = "만 나이. 피보호자(ELDER)는 필수. Graph RAG ELDERLY 판정에 사용.", example = "75")
        Integer age,

        /**
         * 임신 여부. ELDER 회원은 필수, GUARDIAN 은 null 허용.
         * (ELDER 필수 검증은 AuthService 에서 role 과 함께 처리)
         */
        @Schema(description = "임신 여부. 피보호자(ELDER)는 필수. Graph RAG PREGNANCY 판정에 사용.", example = "false")
        Boolean isPregnant
) {
}