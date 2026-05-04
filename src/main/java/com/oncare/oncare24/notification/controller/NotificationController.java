package com.oncare.oncare24.notification.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.notification.dto.NotificationResponse;
import com.oncare.oncare24.notification.dto.UnreadCountResponse;
import com.oncare.oncare24.notification.entity.NotificationHistory;
import com.oncare.oncare24.notification.entity.NotificationType;
import com.oncare.oncare24.notification.repository.NotificationHistoryRepository;
import com.oncare.oncare24.notification.sender.FcmSender;
import com.oncare.oncare24.notification.service.NotificationQueryService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 알림 도메인 API.
 * <p>
 * <b>엔드포인트</b>
 * <ul>
 *     <li>GET   /api/notifications              — 내 알림 목록 (최신순)</li>
 *     <li>GET   /api/notifications/unread-count — 안 읽은 알림 개수 (배너/뱃지용)</li>
 *     <li>PATCH /api/notifications/{id}/read    — 읽음 처리</li>
 *     <li>POST  /api/notifications/test         — [DEV] 본인에게 테스트 푸시 발송 (발표 후 제거)</li>
 * </ul>
 * 모두 본인 수신 알림에만 접근 가능. 권한 검증은 Service에서.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "알림 조회 및 읽음 처리")
@SecurityRequirement(name = "BearerAuth")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final FcmSender fcmSender;

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

    /**
     * [DEV ONLY] 본인에게 테스트 푸시 발송.
     * <p>
     * FCM 풀스택 검증용. 발표 후 제거.
     * <ul>
     *     <li>NotificationHistory row 1개 생성</li>
     *     <li>본인 fcm_token으로 FCM 발송</li>
     *     <li>발송 결과 history에 기록</li>
     * </ul>
     */
    @PostMapping("/test")
    @Operation(summary = "[DEV] 테스트 푸시 발송", description = "본인 fcm_token으로 푸시 1회 발송. 발표 후 제거 예정.")
    @Transactional
    public ApiResponse<Map<String, Object>> sendTestPush(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        User user = userRepository.findById(userId).orElseThrow();

        if (user.getFcmToken() == null || user.getFcmToken().isBlank()) {
            return ApiResponse.success(Map.of(
                    "sent", false,
                    "reason", "fcm_token이 등록되지 않았습니다. 앱에서 한 번 더 로그인해 주세요."
            ));
        }

        String title = "보살핌 테스트 알림";
        String body = "FCM 풀스택이 정상 동작하고 있어요. " + LocalDateTime.now();

        NotificationHistory history = NotificationHistory.builder()
                .recipientId(userId)
                .wardId(null)
                .type(NotificationType.ZONE_EXIT) // 임시. enum에 TEST 종류 없으니 ZONE_EXIT 재사용.
                .title(title)
                .body(body)
                .relatedZoneId(null)
                .build();
        history = historyRepository.save(history);

        boolean fcmOk = fcmSender.send(user.getFcmToken(), title, body);
        history.markFcmSent(fcmOk, LocalDateTime.now());

        log.info("[NOTIFY-TEST] userId={}, fcmOk={}", userId, fcmOk);

        return ApiResponse.success(Map.of(
                "sent", fcmOk,
                "historyId", history.getId(),
                "title", title,
                "body", body
        ));
    }
}