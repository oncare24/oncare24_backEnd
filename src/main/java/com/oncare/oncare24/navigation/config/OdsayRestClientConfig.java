package com.oncare.oncare24.navigation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * ODsay API 호출용 RestClient 설정.
 * <p>
 * 베이스 URL은 OdsayProperties에서 받음. apiKey는 각 호출마다 query param으로 직접 전달
 * (ODsay 인증 방식).
 */
@Configuration
@EnableConfigurationProperties(OdsayProperties.class)
public class OdsayRestClientConfig {

    @Bean(name = "odsayRestClient")
    public RestClient odsayRestClient(OdsayProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
