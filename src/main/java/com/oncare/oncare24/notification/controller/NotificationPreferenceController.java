package com.oncare.oncare24.notification.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.notification.dto.NotificationPreferenceResponse;
import com.oncare.oncare24.notification.dto.UpdateNotificationPreferenceRequest;
import com.oncare.oncare24.notification.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자 알림 설정 API.
 * <p>
 * <b>엔드포인트</b>
 * <ul>
 *     <li>GET   /api/notifications/preferences — 내 알림 설정 (행 없으면 default 자동 생성)</li>
 *     <li>PATCH /api/notifications/preferences — 부분 업데이트 (null 필드는 변경 안 함)</li>
 * </ul>
 * GUARDIAN role만 호출 가능. ELDER 호출 시 403.
 */
@RestController
@RequestMapping("/api/notifications/preferences")
@RequiredArgsConstructor
@Tag(name = "NotificationPreference", description = "보호자 알림 설정")
@SecurityRequirement(name = "BearerAuth")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping
    @Operation(summary = "내 알림 설정 조회")
    public ApiResponse<NotificationPreferenceResponse> getMy(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(preferenceService.getMy(userDetails.getUserId()));
    }

    @PatchMapping
    @Operation(summary = "내 알림 설정 업데이트", description = "null 필드는 변경하지 않음")
    public ApiResponse<NotificationPreferenceResponse> updateMy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateNotificationPreferenceRequest request
    ) {
        return ApiResponse.success(preferenceService.updateMy(userDetails.getUserId(), request));
    }
}