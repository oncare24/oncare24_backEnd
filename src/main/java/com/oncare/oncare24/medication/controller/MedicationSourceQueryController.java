package com.oncare.oncare24.medication.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.medication.dto.MedicationLogSourceResponse;
import com.oncare.oncare24.medication.service.MedicationSourceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/wards/{wardId}")
@RequiredArgsConstructor
@Tag(name = "MedicationSource", description = "복약 암호화 원본 조회")
@SecurityRequirement(name = "BearerAuth")
public class MedicationSourceQueryController {

    private final MedicationSourceQueryService medicationSourceQueryService;

    @GetMapping("/medication-logs/source")
    @Operation(
            summary = "복약 기록 원본 조회",
            description = "encrypted_activity_log에 저장된 암호화 복약 기록 이벤트를 복호화하여 조회합니다."
    )
    public ApiResponse<List<MedicationLogSourceResponse>> findMedicationLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 피보호자 ID", example = "2")
            @PathVariable Long wardId,
            @Parameter(description = "조회할 복약 날짜. 지정하지 않으면 전체 기록을 조회합니다.", example = "2026-05-13")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(
                medicationSourceQueryService.findMedicationLogs(
                        userDetails.getUserId(),
                        wardId,
                        date
                )
        );
    }
}
