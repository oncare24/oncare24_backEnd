package com.oncare.oncare24.drugsafety.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Graph RAG (이정현 NestJS 서버) 연동 설정.
 * application.yml 의 graphrag.* 키에 매핑된다.
 */
@ConfigurationProperties(prefix = "graphrag")
public record GraphRagProperties(
        String baseUrl,
        @DefaultValue("30") int timeoutSeconds,
        @DefaultValue("true") boolean enabled
) {
}