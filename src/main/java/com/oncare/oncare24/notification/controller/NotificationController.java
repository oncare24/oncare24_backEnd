package com.oncare.oncare24.notification.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.notification.dto.NotificationResponse;
import com.oncare.oncare24.notification.dto.UnreadCountResponse;
import com.oncare.oncare24.notification.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 알림 도메인 API.
 * <p>
 * <b>엔드포인트</b>
 * <ul>
 *     <li>GET   /api/notifications              — 내 알림 목록 (최신순)</li>
 *     <li>GET   /api/notifications/unread-count — 안 읽은 알림 개수 (배너/뱃지용)</li>
 *     <li>PATCH /api/notifications/{id}/read    — 읽음 처리</li>
 * </ul>
 * 모두 본인 수신 알림에만 접근 가능. 권한 검증은 Service에서.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "알림 조회 및 읽음 처리")
@SecurityRequirement(name = "BearerAuth")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    @Operation(summary = "내 알림 목록", description = "현재 사용자가 수신자인 모든 알림을 최신순으로 반환.")
    public ApiResponse<List<NotificationResponse>> findMine(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(
                notificationQueryService.findMyNotifications(userDetails.getUserId())
        );
    }

    @GetMapping("/unread-count")
    @Operation(summary = "안 읽은 알림 개수", description = "보호자 홈의 알림 뱃지/배너 카운터.")
    public ApiResponse<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(
                notificationQueryService.countMyUnread(userDetails.getUserId())
        );
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "알림 읽음 처리", description = "본인 수신 알림만 가능. idempotent.")
    public ApiResponse<NotificationResponse> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ApiResponse.success(
                notificationQueryService.markAsRead(userDetails.getUserId(), id)
        );
    }
}