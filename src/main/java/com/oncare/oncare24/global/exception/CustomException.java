package com.oncare.oncare24.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 예외 베이스 클래스.
 * <p>
 * 사용 예시:
 * <pre>{@code
 * // 기본 메시지 사용
 * throw new CustomException(ErrorCode.USER_NOT_FOUND);
 *
 * // 동적 메시지 사용
 * throw new CustomException(ErrorCode.DUPLICATE_EMAIL, "이미 가입된 이메일: " + email);
 * }</pre>
 *
 * GlobalExceptionHandler가 이 예외를 잡아 표준 ApiResponse로 변환합니다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
