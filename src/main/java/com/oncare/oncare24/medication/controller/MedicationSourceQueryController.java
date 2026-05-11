package com.oncare.oncare24.medication.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.medication.dto.MedicationLogSourceResponse;
import com.oncare.oncare24.medication.dto.MedicationScheduleSourceResponse;
import com.oncare.oncare24.medication.service.MedicationSourceQueryService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "MedicationSource", description = "Encrypted medication source queries")
@SecurityRequirement(name = "BearerAuth")
public class MedicationSourceQueryController {

    private final MedicationSourceQueryService medicationSourceQueryService;

    @GetMapping("/medication-schedules/source")
    @Operation(summary = "Find medication schedules from encrypted source events")
    public ApiResponse<List<MedicationScheduleSourceResponse>> findMedicationSchedules(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ApiResponse.success(
                medicationSourceQueryService.findMedicationSchedules(
                        userDetails.getUserId(),
                        wardId,
                        includeInactive
                )
        );
    }

    @GetMapping("/medication-logs/source")
    @Operation(summary = "Find medication logs from encrypted source events")
    public ApiResponse<List<MedicationLogSourceResponse>> findMedicationLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
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
