package com.oncare.oncare24.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 jwt.* 설정을 타입세이프하게 받기 위한 record.
 * <p>
 * Oncare24Application 클래스에 {@code @ConfigurationPropertiesScan}이 있으므로 자동 등록됩니다.
 *
 * <pre>
 * jwt:
 *   secret: ...
 *   access-token-validity-in-seconds: 1800
 *   refresh-token-validity-in-seconds: 1209600
 * </pre>
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidityInSeconds,
        long refreshTokenValidityInSeconds
) {
}
