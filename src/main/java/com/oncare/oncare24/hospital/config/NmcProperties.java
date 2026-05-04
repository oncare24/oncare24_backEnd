package com.oncare.oncare24.hospital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 국립중앙의료원 (공공데이터포털) API 설정.
 * <pre>
 * nmc:
 *   service-key: xK%2B...        # 공공데이터포털 발급 일반 인증키 (Encoding)
 *   hospital-base-url: http://apis.data.go.kr/B552657/HsptlAsembySearchService
 *   timeout-seconds: 5
 *   mock: false                    # true면 실제 호출 대신 가짜 데이터 반환 (개발용)
 * </pre>
 */
@ConfigurationProperties(prefix = "nmc")
public record NmcProperties(
        String serviceKey,
        String hospitalBaseUrl,
        int timeoutSeconds,
        boolean mock
) {
}
