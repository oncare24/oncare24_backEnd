package com.oncare.oncare24.drugsafety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 2차 카카오톡 인증 확정 요청.
 * <p>
 * 1차에서 받은 jti / twoWayTimestamp 를 함께 전송한다.
 * twoWayTimestamp 는 반드시 Long 으로 처리 (Graph RAG 스펙).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(of = {"userName", "jti"})
public class CodefConfirmRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{13}$", message = "주민등록번호 13자리 숫자만 입력해 주세요.")
    private String identity;

    @NotBlank
    private String userName;

    @NotBlank
    @Pattern(regexp = "^\\d{10,11}$", message = "휴대폰 번호는 10~11자리 숫자만 입력해 주세요.")
    private String phoneNo;

    @NotBlank
    private String jti;

    @NotNull
    private Long twoWayTimestamp;
}