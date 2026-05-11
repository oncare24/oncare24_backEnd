package com.oncare.oncare24.analysis.controller;

import com.oncare.oncare24.analysis.dto.AnalysisStateResponse;
import com.oncare.oncare24.analysis.service.AnalysisStateQueryService;
import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "AnalysisState", description = "Latest ward analysis state")
@SecurityRequirement(name = "BearerAuth")
public class AnalysisStateController {

    private final AnalysisStateQueryService analysisStateQueryService;

    @GetMapping
    @Operation(summary = "Find latest medication and inactivity state")
    public ApiResponse<AnalysisStateResponse> findWardAnalysisState(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId
    ) {
        return ApiResponse.success(
                analysisStateQueryService.findWardAnalysisState(userDetails.getUserId(), wardId)
        );
    }
}
