package com.oncare.oncare24.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.auth.jwt.JwtAccessDeniedHandler;
import com.oncare.oncare24.auth.jwt.JwtAuthenticationEntryPoint;
import com.oncare.oncare24.auth.jwt.JwtAuthenticationFilter;
import com.oncare.oncare24.auth.jwt.JwtExceptionFilter;
import com.oncare.oncare24.auth.jwt.JwtProvider;
import com.oncare.oncare24.auth.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security + JWT 설정.
 * <p>
 * <b>필터 체인 순서</b>:
 * <pre>
 *   Request → JwtExceptionFilter → JwtAuthenticationFilter → ... → UsernamePasswordAuthFilter → ...
 * </pre>
 * JwtExceptionFilter가 더 outer라서, JwtAuthenticationFilter에서 던진 예외를 catch해 응답으로 변환합니다.
 *
 * <b>공개 경로</b>: 인증 없이 접근 가능
 * <ul>
 *     <li>POST /api/auth/signup, /api/auth/login, /api/auth/reissue</li>
 *     <li>/api/health/**</li>
 *     <li>Swagger 관련 경로</li>
 * </ul>
 * 그 외 모든 API는 인증 필요.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final ObjectMapper objectMapper;

    private static final String[] PUBLIC_GET_PATHS = {
            "/api/health/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/error"
    };

    private static final String[] PUBLIC_POST_PATHS = {
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/reissue"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        new JwtExceptionFilter(objectMapper),
                        JwtAuthenticationFilter.class
                );
        return http.build();
    }
}
