package com.oncare.oncare24.drugsafety.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.drugsafety.dto.CodefAuthRequest;
import com.oncare.oncare24.drugsafety.dto.CodefAuthResponse;
import com.oncare.oncare24.drugsafety.dto.CodefConfirmRequest;
import com.oncare.oncare24.drugsafety.dto.MedicationAnalysisResponse;
import com.oncare.oncare24.drugsafety.service.DrugSafetyService;
import com.oncare.oncare24.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 복약 안전 분석 (Graph RAG) BFF 컨트롤러.
 * <ul>
 *   <li>POST /api/drug-safety/auth/request - 1차 카카오톡 간편인증 요청 (피보호자 본인만)</li>
 *   <li>POST /api/drug-safety/auth/confirm - 2차 인증 확정 + 처방전 분석 + 캐시</li>
 *   <li>GET  /api/drug-safety/analysis     - 분석 결과 조회 (본인 또는 ACCEPTED 보호자)</li>
 * </ul>
 */
@Tag(name = "Drug Safety", description = "복약 안전 분석 (Graph RAG)")
@RestController
@RequestMapping(value = "/api/drug-safety", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DrugSafetyController {

    private final DrugSafetyService drugSafetyService;

    @PostMapping("/auth/request")
    @Operation(
            summary = "처방전 간편인증 요청 (1차)",
            description = "피보호자(ELDER) 본인만 호출 가능. " +
                    "응답의 jti / twoWayTimestamp 를 클라이언트가 보관한 뒤 confirm 호출 시 동일하게 전달."
    )
    public ApiResponse<CodefAuthResponse> requestAuth(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CodefAuthRequest request
    ) {
        return ApiResponse.success(
                drugSafetyService.requestCodefAuth(userDetails.getUserId(), request)
        );
    }

    @PostMapping("/auth/confirm")
    @Operation(
            summary = "처방전 인증 확정 + 분석 (2차)",
            description = "카카오톡 인증 수락 후 호출. 건강보험공단 처방전을 조회하고 " +
                    "Graph RAG 분석 결과를 한 사용자당 1행으로 캐시(덮어쓰기)한다."
    )
    public ApiResponse<MedicationAnalysisResponse> confirmAuth(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CodefConfirmRequest request
    ) {
        return ApiResponse.success(
                drugSafetyService.confirmCodefAuth(userDetails.getUserId(), request)
        );
    }

    @GetMapping("/analysis")
    @Operation(
            summary = "캐시된 복약 안전 분석 조회",
            description = "wardId 미지정 시 본인(피보호자) 결과 조회. " +
                    "wardId 지정 시 해당 피보호자의 결과 조회 — ACCEPTED 보호자만 허용."
    )
    public ApiResponse<MedicationAnalysisResponse> getAnalysis(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회 대상 피보호자 ID (보호자 시점). 미지정 시 본인 결과.")
            @RequestParam(required = false) Long wardId
    ) {
        return ApiResponse.success(
                drugSafetyService.getAnalysis(userDetails.getUserId(), wardId)
        );
    }

    @PostMapping("/analysis/refresh-request/{wardId}")
    @Operation(
            summary = "피보호자에게 처방전 분석 재실행 요청 (보호자 전용)",
            description = "보호자가 피보호자에게 '처방전 안전 분석을 업데이트해 주세요' 푸시 알림을 보낸다. " +
                    "ACCEPTED 매칭된 보호자만 호출 가능. DB 캐시는 변경되지 않으며, 피보호자가 직접 분석을 다시 실행해야 갱신된다."
    )
    public ApiResponse<Void> requestRefresh(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "재분석 요청 대상 피보호자 ID", example = "2")
            @PathVariable Long wardId
    ) {
        drugSafetyService.requestRefresh(userDetails.getUserId(), wardId);
        return ApiResponse.success();
    }
}