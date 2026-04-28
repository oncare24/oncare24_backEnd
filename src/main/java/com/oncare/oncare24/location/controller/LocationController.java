package com.oncare.oncare24.location.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.location.dto.LastLocationResponse;
import com.oncare.oncare24.location.dto.LocationReportRequest;
import com.oncare.oncare24.location.dto.LocationReportResponse;
import com.oncare.oncare24.location.service.LocationReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 위치 도메인 API.
 * <p>
 * <b>엔드포인트</b>
 * <ul>
 *     <li>POST /api/locations/reports                 — 피보호자 위치 보고 (ELDER 본인만)</li>
 *     <li>GET  /api/locations/last?wardId={id}        — 보호자가 ward의 마지막 위치 조회</li>
 * </ul>
 *
 * <b>권한</b>: 모두 인증 필수. 역할/연동 검증은 Service 레이어에서 수행.
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Tag(name = "Location", description = "위치 보고 및 조회")
@SecurityRequirement(name = "BearerAuth")
public class LocationController {

    private final LocationReportService locationReportService;

    @PostMapping("/reports")
    @Operation(
            summary = "위치 보고",
            description = "피보호자가 자신의 현재 위치를 보고합니다. 30분 주기 백그라운드 자동 보고 + " +
                    "포그라운드 진입 시 즉시 보고 + FCM 깨우기 응답 시 사용. " +
                    "GPS accuracy가 100m를 초과하면 서버에서 silent drop (200 OK + stored=false)."
    )
    public ApiResponse<LocationReportResponse> report(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody LocationReportRequest request
    ) {
        return ApiResponse.success(
                locationReportService.report(userDetails.getUserId(), request)
        );
    }

    @GetMapping("/last")
    @Operation(
            summary = "마지막 위치 조회",
            description = "보호자가 자신과 ACCEPTED 상태로 연동된 피보호자의 마지막 위치 + 단말 상태를 조회합니다. " +
                    "보호자 홈 화면 카드 데이터 소스."
    )
    public ApiResponse<LastLocationResponse> getLast(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long wardId
    ) {
        return ApiResponse.success(
                locationReportService.getLastLocation(userDetails.getUserId(), wardId)
        );
    }
}