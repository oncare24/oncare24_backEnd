package com.oncare.oncare24.hospital.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.hospital.dto.RecommendRequest;
import com.oncare.oncare24.hospital.dto.RecommendResponse;
import com.oncare.oncare24.hospital.service.HospitalRecommendService;
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
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
@Tag(name = "Hospital", description = "LLM 문진 기반 병원 추천")
@SecurityRequirement(name = "BearerAuth")
public class HospitalRecommendController {

    private final HospitalRecommendService recommendService;

    @PostMapping("/recommend")
    @Operation(
            summary = "증상 기반 병원 추천",
            description = """
                    사용자가 입력한 증상을 LLM으로 분석하여 적합한 진료과/응급도를 결정한 뒤,
                    위치 기반으로 가까운 병원/응급의료기관을 추천합니다.

                    위치 결정 우선순위:
                    1) 요청 본문의 lat/lon (현재 GPS)
                    2) 최근 5분 내 보고된 위치
                    3) 본인 안전구역 첫 번째 (보통 '집')

                    응급도가 HIGH로 분류되면 일반 병의원 대신 응급의료기관 리스트를 반환하며,
                    응급 안내 문구(emergencyAlert)도 함께 포함됩니다.
                    """
    )
    public ApiResponse<RecommendResponse> recommend(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RecommendRequest request
    ) {
        return ApiResponse.success(recommendService.recommend(userDetails.getUserId(), request));
    }
}
