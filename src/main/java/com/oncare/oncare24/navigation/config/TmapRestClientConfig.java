package com.oncare.oncare24.navigation.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * TMAP API 호출용 RestClient Bean.
 * <p>
 * TMAP은 매 요청에 {@code appKey} 헤더가 필수. 여기서 자동 주입한다.
 */
@Configuration
@RequiredArgsConstructor
public class TmapRestClientConfig {

    private final TmapProperties tmapProperties;

    @Bean(name = "tmapRestClient")
    public RestClient tmapRestClient() {
        Duration timeout = Duration.ofSeconds(tmapProperties.timeoutSeconds());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("appKey", tmapProperties.appKey())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
