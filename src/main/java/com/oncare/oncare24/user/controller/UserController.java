package com.oncare.oncare24.user.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.user.dto.UpdateFcmTokenRequest;
import com.oncare.oncare24.user.dto.UserResponse;
import com.oncare.oncare24.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 정보")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "내 정보 조회",
            description = "현재 로그인한 사용자의 정보를 반환합니다. accessToken 필수."
    )
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(userService.getMe(userDetails.getUserId()));
    }

    @PatchMapping("/me/fcm-token")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "FCM 토큰 등록/갱신",
            description = """
                    프론트가 Firebase에서 발급받은 FCM 토큰을 서버에 저장합니다.
                    <br>호출 시점: 로그인 직후, 그리고 Firebase가 토큰을 회전시킨 onTokenRefresh 콜백.
                    <br>같은 토큰이 이미 저장돼 있으면 변경 사항 없음.
                    """
    )
    public ApiResponse<Void> updateFcmToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateFcmTokenRequest request
    ) {
        userService.updateFcmToken(userDetails.getUserId(), request.fcmToken());
        return ApiResponse.success(null);
    }
}