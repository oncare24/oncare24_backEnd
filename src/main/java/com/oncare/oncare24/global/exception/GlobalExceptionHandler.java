package com.oncare.oncare24.global.exception;

import com.oncare.oncare24.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리기.
 * <p>
 * 모든 컨트롤러에서 발생한 예외를 잡아 표준 ApiResponse 형태로 변환합니다.
 * 새로운 예외 처리가 필요하면 {@code @ExceptionHandler} 메서드를 추가하세요.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직에서 의도적으로 던진 예외.
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(
            CustomException e, HttpServletRequest request) {
        log.warn("[CustomException] {} {} - [{}] {}",
                request.getMethod(), request.getRequestURI(),
                e.getErrorCode().getCode(), e.getMessage());
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, e.getMessage()));
    }

    /**
     * @Valid 검증 실패 (RequestBody).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        log.warn("[Validation] {}", message);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, message));
    }

    /**
     * @ModelAttribute 바인딩 실패 (form/query).
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        log.warn("[Bind] {}", message);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, message));
    }

    /**
     * 경로 변수/쿼리 파라미터 타입 불일치.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[TypeMismatch] param={}, value={}", e.getName(), e.getValue());
        return ResponseEntity.status(ErrorCode.INVALID_TYPE_VALUE.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_TYPE_VALUE));
    }

    /**
     * 지원하지 않는 HTTP 메서드.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[MethodNotAllowed] {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 잘못된 JSON 본문.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[NotReadable] {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.REQUEST_BODY_NOT_READABLE.getStatus())
                .body(ApiResponse.error(ErrorCode.REQUEST_BODY_NOT_READABLE));
    }

    /**
     * 매핑되지 않은 URL.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException e) {
        log.warn("[NoHandler] {} {}", e.getHttpMethod(), e.getRequestURL());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * Spring Security의 권한 거부.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("[AccessDenied] {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.HANDLE_ACCESS_DENIED.getStatus())
                .body(ApiResponse.error(ErrorCode.HANDLE_ACCESS_DENIED));
    }

    /**
     * 그 외 모든 예외 - 절대 클라이언트로 노출되면 안 되는 정보를 마스킹.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("[Unhandled] {} {} - {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
