package com.oncare.oncare24.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oncare.oncare24.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 모든 API 응답의 표준 포맷.
 * <p>
 * 성공 응답:
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { ... }
 * }
 * }</pre>
 * 실패 응답:
 * <pre>{@code
 * {
 *   "success": false,
 *   "error": { "code": "U001", "message": "사용자를 찾을 수 없습니다." }
 * }
 * }</pre>
 *
 * Controller 사용 예시:
 * <pre>{@code
 * @GetMapping("/{id}")
 * public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
 *     return ApiResponse.success(userService.getUser(id));
 * }
 * }</pre>
 *
 * 실패 응답은 직접 만들 일이 거의 없고, GlobalExceptionHandler가 자동 생성합니다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorPayload error;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> error(ErrorCode code) {
        return new ApiResponse<>(false, null, new ErrorPayload(code.getCode(), code.getMessage()));
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorPayload(code.getCode(), message));
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorPayload {
        private final String code;
        private final String message;
    }
}
