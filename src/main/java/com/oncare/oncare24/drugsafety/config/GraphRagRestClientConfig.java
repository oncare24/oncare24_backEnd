package com.oncare.oncare24.drugsafety.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Graph RAG 호출용 RestClient.
 * <p>
 * 별도 RestClient를 둔 이유: codef 응답 시간이 최대 4초+ (LLM 포함)로 길어
 * 다른 외부 API(카카오/NMC) 와 timeout 정책이 다르다.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(GraphRagProperties.class)
public class GraphRagRestClientConfig {

    private final GraphRagProperties properties;

    @Bean(name = "graphRagRestClient")
    public RestClient graphRagRestClient() {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(buildRequestFactory(Duration.ofSeconds(properties.timeoutSeconds())))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(timeout.toMillis(), 5_000L));
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }
}