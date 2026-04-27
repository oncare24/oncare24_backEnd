package com.oncare.oncare24.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.global.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JwtAuthenticationFilter보다 앞에 위치하여, 그 안에서 던진 CustomException을 잡아
 * 표준 ApiResponse 포맷으로 응답합니다.
 * <p>
 * 필터 체인에서는 RestControllerAdvice(GlobalExceptionHandler)가 동작하지 않으므로 별도 필터가 필요합니다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtExceptionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (CustomException e) {
            log.warn("[JWT Filter] {} {} - [{}] {}",
                    request.getMethod(), request.getRequestURI(),
                    e.getErrorCode().getCode(), e.getMessage());
            sendErrorResponse(response, e.getErrorCode());
        }
    }

    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.error(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
