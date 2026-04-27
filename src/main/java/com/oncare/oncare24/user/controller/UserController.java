package com.oncare.oncare24.user.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.user.dto.UserResponse;
import com.oncare.oncare24.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
}