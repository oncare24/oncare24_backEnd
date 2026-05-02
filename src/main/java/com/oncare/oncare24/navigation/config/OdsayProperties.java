package com.oncare.oncare24.navigation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ODsay API 설정.
 * <p>
 * application.yml의 {@code odsay:} 블록과 매핑.
 * <pre>
 * odsay:
 *   api-key: ${ODSAY_API_KEY}
 *   base-url: https://api.odsay.com/v1/api
 *   mock: ${ODSAY_MOCK:false}
 * </pre>
 *
 * @param apiKey  ODsay에서 발급받은 인증 키
 * @param baseUrl API 베이스 URL (보통 변경 불필요)
 * @param mock    {@code true}면 MockOdsayClient 활성, {@code false}면 RealOdsayClient
 */
@ConfigurationProperties(prefix = "odsay")
public record OdsayProperties(
        String apiKey,
        String baseUrl,
        boolean mock
) {
    public OdsayProperties {
        // 컴팩트 생성자 - 기본값 보정
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.odsay.com/v1/api";
        }
    }
}
