package com.oncare.oncare24.hospital.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 API 호출용 RestClient.
 * <p>
 * <b>RestClient 선택 이유</b>: WebClient는 Reactive 의존성을 끌어오고, RestTemplate는 Deprecated 방향.
 * Spring 6.1+의 RestClient는 동기 API + 모던 빌더 + Spring Boot 3.4 권장 클라이언트.
 *
 * <b>두 개로 분리한 이유</b>: OpenAI는 응답이 길어서 timeout이 더 길어야 함 (LLM 추론 시간).
 * NMC는 빠른 응답을 기대하므로 짧은 timeout으로 fail-fast.
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final OpenAiProperties openAiProperties;
    private final NmcProperties nmcProperties;

    @Bean(name = "openAiRestClient")
    public RestClient openAiRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(buildRequestFactory(Duration.ofSeconds(openAiProperties.timeoutSeconds())))
                .defaultHeader("Authorization", "Bearer " + openAiProperties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean(name = "nmcRestClient")
    public RestClient nmcRestClient() {
        return RestClient.builder()
                .requestFactory(buildRequestFactory(Duration.ofSeconds(nmcProperties.timeoutSeconds())))
                .build();
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }
}
