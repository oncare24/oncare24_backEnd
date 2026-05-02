package com.oncare.oncare24.kakao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로컬 API 설정.
 * <p>
 * REST API 키는 백엔드에서만 보관 (프론트 노출 금지). 클라이언트는 백엔드 프록시를 통해서만 호출.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoLocalProperties(
        String restApiKey,
        String baseUrl,
        int timeoutSeconds
) {
}