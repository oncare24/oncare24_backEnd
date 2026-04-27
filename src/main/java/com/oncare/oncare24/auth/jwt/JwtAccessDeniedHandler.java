package com.oncare.oncare24.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증은 됐지만 해당 리소스에 권한이 없을 때 호출됩니다 (예: ELDER가 GUARDIAN 전용 API 호출).
 * 표준 ApiResponse 포맷으로 403을 응답합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[AccessDenied] {} {} - {}", request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());
        ErrorCode code = ErrorCode.HANDLE_ACCESS_DENIED;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code)));
    }
}
