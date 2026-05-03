package com.oncare.oncare24.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 멀티턴 채팅의 한 턴 (메시지 한 개).
 *
 * <p><b>role</b>:
 * <ul>
 *   <li>{@code "bot"} - 봇이 던진 메시지 (인사, 후속 질문, 분석 완료 멘트)</li>
 *   <li>{@code "user"} - 사용자 입력</li>
 * </ul>
 *
 * <p>OpenAI Chat API 호출 시 backend 내부에서 {@code "assistant"}/{@code "user"}로 변환됨.
 *
 * @param role 화자 구분 ("bot" | "user")
 * @param text 메시지 본문 (1~500자)
 */
public record ChatTurn(

        @NotBlank(message = "role은 필수입니다.")
        @NotNull
        String role,

        @NotBlank(message = "text는 필수입니다.")
        @Size(max = 500, message = "메시지는 500자 이하로 입력해주세요.")
        String text
) {
}
