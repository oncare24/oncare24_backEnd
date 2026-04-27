package com.oncare.oncare24.auth.controller;

import com.oncare.oncare24.auth.dto.LoginRequest;
import com.oncare.oncare24.auth.dto.ReissueRequest;
import com.oncare.oncare24.auth.dto.SignUpRequest;
import com.oncare.oncare24.auth.dto.SignUpResponse;
import com.oncare.oncare24.auth.dto.TokenResponse;
import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.auth.service.AuthService;
import com.oncare.oncare24.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입 / 로그인 / 로그아웃 / 토큰 재발급")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "회원가입",
            description = "전화번호(하이픈 제외) + 비밀번호(8자 이상) + 이름 + 역할(ELDER/GUARDIAN)로 회원가입합니다."
    )
    public ApiResponse<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ApiResponse.success(authService.signUp(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "로그인",
            description = "전화번호 + 비밀번호로 로그인합니다. accessToken(30분) + refreshToken(14일)을 발급합니다."
    )
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/reissue")
    @Operation(
            summary = "토큰 재발급",
            description = "refreshToken으로 새 accessToken/refreshToken 페어를 발급합니다 (Refresh Token Rotation)."
    )
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponse.success(authService.reissue(request));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "로그아웃",
            description = "Redis에 저장된 refreshToken을 삭제합니다. accessToken은 만료까지 유효 (현재 정책)."
    )
    public ApiResponse<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getUserId());
        return ApiResponse.success();
    }
}
