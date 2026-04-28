package com.oncare.oncare24.hospital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI API 설정.
 * <pre>
 * openai:
 *   api-key: sk-proj-...
 *   model: gpt-4o-mini
 *   timeout-seconds: 10
 *   enabled: true       # false면 키워드 fallback만 동작 (오프라인 개발용)
 * </pre>
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String model,
        int timeoutSeconds,
        boolean enabled
) {
}
