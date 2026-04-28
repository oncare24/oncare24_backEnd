package com.oncare.oncare24;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 보살핌(Oncare24) 백엔드 애플리케이션 진입점.
 * <p>
 * - {@code @ConfigurationPropertiesScan}: {@code @ConfigurationProperties} 기반 record/class를 자동 등록.
 *   (현재: {@link com.oncare.oncare24.auth.jwt.JwtProperties})
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Oncare24Application {

	public static void main(String[] args) {
        // 디버그 - 확인 후 삭제 가능
        System.out.println("OPENAI_API_KEY length: " +
                (System.getenv("OPENAI_API_KEY") == null ? "NULL" : System.getenv("OPENAI_API_KEY").length()));
        System.out.println("NMC_MOCK: " + System.getenv("NMC_MOCK"));
		SpringApplication.run(Oncare24Application.class, args);
	}

}
