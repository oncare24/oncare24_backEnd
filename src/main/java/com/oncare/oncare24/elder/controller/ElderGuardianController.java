package com.oncare.oncare24.elder.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.elder.dto.MyGuardianResponse;
import com.oncare.oncare24.elder.service.ElderGuardianService;
import com.oncare.oncare24.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 어르신 시점 — 내 보호자 목록.
 * 어르신 앱이 위치 추적 시작 전에 이 API로 보호자 매핑 유무를 확인한다.
 */
@RestController
@RequestMapping("/api/elder/guardians")
@RequiredArgsConstructor
@Tag(name = "ElderGuardian", description = "어르신 시점 - 내 보호자 목록")
@SecurityRequirement(name = "BearerAuth")
public class ElderGuardianController {

    private final ElderGuardianService elderGuardianService;

    @GetMapping
    @Operation(
            summary = "내 보호자 목록 조회",
            description = "ELDER 역할만 호출 가능. ACCEPTED 상태 보호자 매핑 목록을 반환. " +
                    "빈 배열이면 보호자 매핑이 0개 = 위치 추적·민감 데이터 저장 불가 상태."
    )
    public ApiResponse<List<MyGuardianResponse>> findMyGuardians(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(
                elderGuardianService.findMyGuardians(userDetails.getUserId())
        );
    }
}