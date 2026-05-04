package com.oncare.oncare24.sos.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.sos.dto.SosEventDetailResponse;
import com.oncare.oncare24.sos.dto.SosEventResponse;
import com.oncare.oncare24.sos.dto.SosTriggerRequest;
import com.oncare.oncare24.sos.service.SosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * SOS 긴급 호출 API.
 * <p>
 * <b>엔드포인트</b>
 * <ul>
 *     <li>POST /api/sos               — 호출 발행. ELDER만. 5초 쿨다운.</li>
 *     <li>GET  /api/sos/{eventId}     — 이벤트 상세. 보호자 SosLocationView 진입 시.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/sos")
@RequiredArgsConstructor
@Tag(name = "SOS", description = "긴급 호출")
@SecurityRequirement(name = "BearerAuth")
public class SosController {

    private final SosService sosService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "SOS 긴급 호출",
            description = "피보호자(ELDER)가 호출하면 연동된 모든 ACCEPTED 보호자에게 즉시 FCM 알림이 발송됩니다. " +
                    "10분 내 미확인 시 자동으로 SMS 폴백. 5초 내 재호출은 R001 에러."
    )
    public ApiResponse<SosEventResponse> trigger(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SosTriggerRequest request
    ) {
        return ApiResponse.success(sosService.trigger(userDetails.getUserId(), request));
    }

    @GetMapping("/{eventId}")
    @Operation(
            summary = "SOS 이벤트 상세",
            description = "보호자가 알림 탭 시 SosLocationView로 이동하면서 호출. 호출 좌표·피보호자 이름/전화번호 등 반환. " +
                    "본인 호출 이력 또는 ACCEPTED 연결된 보호자만 열람 가능."
    )
    public ApiResponse<SosEventDetailResponse> getDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long eventId
    ) {
        return ApiResponse.success(sosService.getDetail(userDetails.getUserId(), eventId));
    }
}