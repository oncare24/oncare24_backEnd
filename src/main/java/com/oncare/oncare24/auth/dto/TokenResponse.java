package com.oncare.oncare24.auth.dto;

/**
 * 로그인/재발급 시 반환되는 토큰 페어.
 *
 * @param accessToken           Access Token (Authorization 헤더에 Bearer로 사용)
 * @param refreshToken          Refresh Token (Access Token 만료 시 재발급에 사용)
 * @param accessTokenExpiresIn  Access Token 만료까지의 초 (클라이언트에서 만료 시점 계산용)
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn
) {
}
