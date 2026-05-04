package com.oncare.oncare24.global.config;

import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SOLAPI Java SDK 초기화.
 * <p>
 * <b>동작 조건</b>: {@code sms.enabled=true} 일 때만 빈 등록.
 * 끄면 SOLAPI 클라이언트 초기화 안 함 → 키 없어도 앱 정상 부팅 (개발 친화).
 *
 * <b>발신번호 등록 필수</b>: SOLAPI 콘솔에서 "발신번호 등록" 사전 승인 필요.
 * 미등록 번호로 발송 시도 시 SOLAPI가 거부함 (한국 통신사 규제).
 *
 * <b>비용</b>: SMS(90바이트=한글 45자 이내) ≈ 9~13P, LMS(2000바이트) ≈ 30P.
 * 본문이 한글 45자 초과하면 자동 LMS로 분류되니 본문 길이 주의 (비용 ~3배).
 *
 * <b>Gson 명시 의존</b>: SDK 내부에서 com.google.code.gson 사용. firebase-admin이 동일 라이브러리를 가져오므로
 * 충돌 방지를 위해 별도 의존 추가 안 함. 만약 ClassNotFoundException 발생 시 build.gradle에 gson 명시 추가.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "sms.enabled", havingValue = "true")
public class SolapiConfig {

    @Value("${sms.api-key}")
    private String apiKey;

    @Value("${sms.api-secret}")
    private String apiSecret;

    @Value("${sms.base-url}")
    private String baseUrl;

    @Bean
    public DefaultMessageService solapiMessageService() {
        log.info("[SMS-INIT] SOLAPI initialized. baseUrl={}", baseUrl);
        return NurigoApp.INSTANCE.initialize(apiKey, apiSecret, baseUrl);
    }
}