package com.oncare.oncare24.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 시스템 전체의 에러 코드 정의.
 * <p>
 * 코드 체계:
 * <ul>
 *     <li>C0xx - 공통 (Common)</li>
 *     <li>A0xx - 인증/인가 (Auth)</li>
 *     <li>U0xx - 사용자 (User)</li>
 *     <li>G0xx - 보호자 연동 (Guardian)</li>
 *     <li>S0xx - 안전 구역 (SafeZone)</li>
 *     <li>L0xx - 위치/모니터링 (Location)</li>
 *     <li>M0xx - 복약 (Medication)</li>
 *     <li>H0xx - 병원/문진 (Hospital)</li>
 *     <li>N0xx - 알림 (Notification)</li>
 * </ul>
 * 새 도메인 추가 시 prefix를 정해 일관성 있게 추가합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // === 공통 ===
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "허용되지 않은 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C004", "타입이 올바르지 않습니다."),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C005", "접근이 거부되었습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C006", "요청한 리소스를 찾을 수 없습니다."),
    REQUEST_BODY_NOT_READABLE(HttpStatus.BAD_REQUEST, "C007", "요청 본문을 파싱할 수 없습니다."),

    // === 인증/인가 (Step 5~6에서 사용) ===
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A004", "이메일 또는 비밀번호가 올바르지 않습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A005", "리프레시 토큰을 찾을 수 없습니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "A006", "리프레시 토큰이 일치하지 않습니다."),

    // === 사용자 (Step 4~6에서 사용) ===
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "U003", "이미 사용 중인 전화번호입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
