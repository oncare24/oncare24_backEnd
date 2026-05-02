package com.oncare.oncare24.navigation.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.navigation.dto.RouteRequest;
import com.oncare.oncare24.navigation.dto.TransitRouteResponse;
import com.oncare.oncare24.navigation.dto.WalkingRouteResponse;
import com.oncare.oncare24.navigation.service.NavigationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/navigation")
@RequiredArgsConstructor
@Tag(name = "Navigation", description = "길안내 (도보 / 대중교통)")
@SecurityRequirement(name = "BearerAuth")
public class NavigationController {

    private final NavigationService navigationService;

    @PostMapping("/walking")
    @Operation(
            summary = "도보 길안내",
            description = """
                    출발지에서 도착지까지 보행자 경로를 안내합니다.
                    응답은 단계별 안내 카드 리스트 (출발 → 직진/회전/횡단 → 도착).
                    
                    1km 이내 가까운 병원에 권장.
                    """
    )
    public ApiResponse<WalkingRouteResponse> getWalkingRoute(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RouteRequest request
    ) {
        return ApiResponse.success(navigationService.getWalkingRoute(userDetails.getUserId(), request));
    }

    @PostMapping("/transit")
    @Operation(
            summary = "대중교통 길안내",
            description = """
                    출발지에서 도착지까지 대중교통 경로를 안내합니다.
                    응답은 [출발지 도보 → 버스/지하철 → 환승 도보 → ... → 도착지 도보] 카드 리스트.
                    
                    1km 이상 거리에 권장. 너무 가까우면 검색 결과가 없을 수 있습니다 (NO_TRANSIT_ROUTE).
                    """
    )
    public ApiResponse<TransitRouteResponse> getTransitRoute(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RouteRequest request
    ) {
        return ApiResponse.success(navigationService.getTransitRoute(userDetails.getUserId(), request));
    }
}
