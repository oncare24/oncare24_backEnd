package com.oncare.oncare24.drugsafety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 1차 카카오톡 간편인증 요청 (프론트 → 백엔드).
 * <p>
 * 백엔드는 이 정보를 Graph RAG /drug/codef/request 로 그대로 프록시한다.
 * 주민번호/전화번호는 민감정보이므로 {@link ToString}에서 제외하여 로그 노출을 방지한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(of = {"userName"})
public class CodefAuthRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{13}$", message = "주민등록번호 13자리 숫자만 입력해 주세요.")
    private String identity;

    @NotBlank
    private String userName;

    @NotBlank
    @Pattern(regexp = "^\\d{10,11}$", message = "휴대폰 번호는 10~11자리 숫자만 입력해 주세요.")
    private String phoneNo;
}