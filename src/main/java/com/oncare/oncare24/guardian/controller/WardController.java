package com.oncare.oncare24.guardian.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.guardian.dto.WardResponse;
import com.oncare.oncare24.guardian.service.WardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.List;

/**
 * 보호자 시점 — 내 피보호자 목록.
 * <p>
 * 이 단일 엔드포인트로 보호자 홈의 카드 리스트를 그린다.
 * 9-D에서 useMyWards 훅이 이 응답을 받아 MOCK_PROTEGES를 대체.
 */
@RestController
@RequestMapping("/api/guardian/wards")
@RequiredArgsConstructor
@Tag(name = "Ward", description = "보호자 시점 - 내 피보호자 목록")
@SecurityRequirement(name = "BearerAuth")
public class WardController {

    private final WardService wardService;

    @GetMapping
    @Operation(
            summary = "내 피보호자 목록 + 현재 상태",
            description = "GUARDIAN 역할만 호출 가능. ACCEPTED 매칭의 ward 정보 + 단말/안전구역 상태를 종합해 반환. " +
                    "ACCEPTED 시각(updatedAt) 최신순."
    )
    public ApiResponse<List<WardResponse>> findMyWards(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(wardService.findMyWards(userDetails.getUserId()));
    }

    @DeleteMapping("/{wardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "피보호자 연결 해제",
            description = "GUARDIAN만 호출 가능. 본인과 ACCEPTED 매칭된 피보호자와의 연결을 끊는다. 매칭이 없으면 G001."
    )
    public void unlinkWard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId
    ) {
        wardService.unlinkWard(userDetails.getUserId(), wardId);
    }
}