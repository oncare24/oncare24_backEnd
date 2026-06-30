package com.oncare.oncare24.kakao.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 카카오 로컬 API 호출용 RestClient.
 * <p>
 * Authorization 헤더(KakaoAK {REST_API_KEY}) 기본 부착.
 * 별도 RestClient를 둔 이유: 다른 외부 연동과 인증 헤더·timeout 정책이 다르기 때문.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KakaoLocalProperties.class)
public class KakaoRestClientConfig {

    private final KakaoLocalProperties properties;

    @Bean(name = "kakaoRestClient")
    public RestClient kakaoRestClient() {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(buildRequestFactory(Duration.ofSeconds(properties.timeoutSeconds())))
                .defaultHeader("Authorization", "KakaoAK " + properties.restApiKey())
                .build();
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }
}