package com.oncare.oncare24.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 사용자가 보호 API에 접근할 때 호출됩니다 (보통 토큰 누락).
 * 표준 ApiResponse 포맷으로 401을 응답합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[Unauthorized] {} {} - {}", request.getMethod(), request.getRequestURI(), authException.getMessage());
        ErrorCode code = ErrorCode.UNAUTHORIZED;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code)));
    }
}
