package com.oncare.oncare24.global.controller;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 동작 확인용 헬스 체크 엔드포인트.
 * <p>
 * Step 1~3 셋업이 제대로 됐는지 빠르게 검증하기 위해 둡니다.
 * 실제 운영에서는 spring-boot-starter-actuator의 /actuator/health로 대체하는 게 일반적입니다.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health Check", description = "서버 동작 확인")
public class HealthController {

    @GetMapping
    @Operation(summary = "서버 헬스 체크", description = "서버가 정상 동작 중이면 200 OK를 반환합니다.")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "service", "oncare24",
                "timestamp", LocalDateTime.now()
        ));
    }

    /**
     * GlobalExceptionHandler가 CustomException을 잘 잡는지 확인용.
     * 호출 시 항상 USER_NOT_FOUND 에러 응답을 반환합니다.
     */
    @GetMapping("/error-test")
    @Operation(summary = "에러 응답 포맷 테스트", description = "GlobalExceptionHandler 동작 확인용. 항상 404 에러를 반환합니다.")
    public ApiResponse<Void> errorTest() {
        throw new CustomException(ErrorCode.USER_NOT_FOUND);
    }
}
