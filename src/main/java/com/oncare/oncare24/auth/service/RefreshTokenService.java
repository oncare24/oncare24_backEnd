package com.oncare.oncare24.auth.service;

import com.oncare.oncare24.auth.jwt.JwtProperties;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Refresh Token을 Redis에 보관/관리합니다.
 * <p>
 * Redis Key 구조: {@code refresh:{userId} → refreshToken}
 * <br>TTL: jwt.refresh-token-validity-in-seconds (기본 14일)
 * <p>
 * <b>Why Redis?</b>
 * <ul>
 *     <li>로그아웃 시 즉시 토큰 무효화 (DB 트랜잭션 불필요)</li>
 *     <li>TTL 자동 만료 (수동 청소 불필요)</li>
 *     <li>토큰 회전(rotation) 시 빠른 교체</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                buildKey(userId),
                refreshToken,
                Duration.ofSeconds(jwtProperties.refreshTokenValidityInSeconds())
        );
    }

    public String findByUserId(Long userId) {
        String value = redisTemplate.opsForValue().get(buildKey(userId));
        if (value == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        return value;
    }

    public void delete(Long userId) {
        redisTemplate.delete(buildKey(userId));
    }

    /**
     * 클라이언트가 보낸 refresh token이 Redis에 저장된 것과 일치하는지 검증.
     * 다르면 (=다른 기기에서 재로그인되어 갱신됨) 거부.
     */
    public void validate(Long userId, String refreshToken) {
        String stored = findByUserId(userId);
        if (!stored.equals(refreshToken)) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_MISMATCH);
        }
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
