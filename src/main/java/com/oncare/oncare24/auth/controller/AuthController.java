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
@Tag(name = "Auth", description = "계정 생성 및 인증")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "회원가입",
            description = "피보호자 또는 보호자 계정을 생성합니다. 회원가입 시 사용자별 ML-KEM 키쌍이 생성되어 OpenBao에 저장됩니다."
    )
    public ApiResponse<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ApiResponse.success(authService.signUp(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "로그인",
            description = "전화번호와 비밀번호로 로그인하고 JWT Access Token과 Refresh Token을 발급받습니다."
    )
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/reissue")
    @Operation(
            summary = "토큰 재발급",
            description = "Refresh Token을 사용해 새로운 Access Token과 Refresh Token을 발급받습니다."
    )
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponse.success(authService.reissue(request));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "로그아웃",
            description = "현재 로그인한 사용자의 Refresh Token을 무효화합니다. 이 API는 인증된 사용자만 호출할 수 있습니다."
    )
    public ApiResponse<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getUserId());
        return ApiResponse.success();
    }
}
