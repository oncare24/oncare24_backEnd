package com.oncare.oncare24.navigation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TMAP API 설정.
 * <pre>
 * tmap:
 *   app-key: l7xx...                  # SK 오픈 API 포털 발급 AppKey
 *   pedestrian-url: https://apis.openapi.sk.com/tmap/routes/pedestrian
 *   transit-url:    https://apis.openapi.sk.com/transit/routes
 *   timeout-seconds: 5
 *   mock: false                        # true면 가짜 응답
 * </pre>
 */
@ConfigurationProperties(prefix = "tmap")
public record TmapProperties(
        String appKey,
        String pedestrianUrl,
        String transitUrl,
        int timeoutSeconds,
        boolean mock
) {
}
