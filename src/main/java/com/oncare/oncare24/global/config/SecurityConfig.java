package com.oncare.oncare24.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * <b>⚠️ 임시 보안 설정 (Step 1~3 단계).</b>
 * <p>
 * spring-boot-starter-security 의존성이 추가되어 있으면 기본적으로 모든 요청에 인증이 걸립니다.
 * 하지만 Step 1~3 단계에서는 아직 사용자/JWT 인프라가 없으므로, 모든 요청을 일단 통과시킵니다.
 * <p>
 * <b>Step 5에서 JwtAuthenticationFilter가 붙은 진짜 SecurityConfig로 교체됩니다.</b>
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
