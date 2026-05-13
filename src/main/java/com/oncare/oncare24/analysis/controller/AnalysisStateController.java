package com.oncare.oncare24.analysis.controller;

import com.oncare.oncare24.analysis.dto.AnalysisStateResponse;
import com.oncare.oncare24.analysis.service.AnalysisStateQueryService;
import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wards/{wardId}/analysis-state")
@RequiredArgsConstructor
@Tag(name = "AnalysisState", description = "복약 및 미활동 분석 상태")
@SecurityRequirement(name = "BearerAuth")
public class AnalysisStateController {

    private final AnalysisStateQueryService analysisStateQueryService;

    @GetMapping
    @Operation(
            summary = "최신 분석 상태 조회",
            description = "피보호자의 최신 복약 분석 상태와 미활동 분석 상태를 조회합니다. 이 API는 분석 실행 API가 아니라 저장된 최신 분석 결과를 조회하는 API입니다."
    )
    public ApiResponse<AnalysisStateResponse> findWardAnalysisState(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 피보호자 ID", example = "2")
            @PathVariable Long wardId
    ) {
        return ApiResponse.success(
                analysisStateQueryService.findWardAnalysisState(userDetails.getUserId(), wardId)
        );
    }
}
