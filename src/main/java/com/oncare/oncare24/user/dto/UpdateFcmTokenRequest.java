package com.oncare.oncare24.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FCM 토큰 등록/갱신 요청.
 * <p>
 * <b>호출 시점</b>:
 * <ul>
 *     <li>로그인 직후 (앱이 발급받은 토큰을 서버에 등록)</li>
 *     <li>토큰 갱신 리스너 (Firebase 가 토큰을 회전시켰을 때)</li>
 * </ul>
 *
 * <b>토큰 형식</b>: FCM 토큰은 보통 140~200자의 영문/숫자/특수문자 혼합. 길이 검증만 느슨하게.
 * 잘못된 토큰이면 발송 시점에 FirebaseMessaging 이 INVALID_ARGUMENT 응답하므로 컬럼 검증 깐깐할 필요 없음.
 *
 * <b>왜 record 가 아닌 class</b>: 다른 DTO들과 일관성 유지(SignupRequest 등이 class).
 * record 도 동작하지만 프로젝트 컨벤션에 맞춤.
 */
public record UpdateFcmTokenRequest(
        @NotBlank(message = "FCM 토큰은 비어 있을 수 없어요.")
        @Size(max = 255, message = "FCM 토큰이 너무 길어요.")
        String fcmToken
) {
}