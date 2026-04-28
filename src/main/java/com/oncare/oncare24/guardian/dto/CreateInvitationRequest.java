package com.oncare.oncare24.guardian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 보호자 → 피보호자 초대 생성 요청.
 * <p>
 * - wardPhone: 하이픈 제거 11자리(SignUpRequest와 동일 패턴).
 * - relationship: 선택. 보호자가 "어머니"/"아버지" 등으로 카드에 표시할 라벨.
 */
public record CreateInvitationRequest(

        @NotBlank(message = "어르신 전화번호를 입력해주세요.")
        @Pattern(
                regexp = "^01[0-9]\\d{7,8}$",
                message = "전화번호는 하이픈 없이 숫자만 입력해주세요."
        )
        String wardPhone,

        @Size(max = 20, message = "관계는 20자 이내로 입력해주세요.")
        String relationship
) {
}