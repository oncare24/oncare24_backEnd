package com.oncare.oncare24.medication.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.medication.dto.CreateMedicationLogRequest;
import com.oncare.oncare24.medication.dto.MedicationLogResponse;
import com.oncare.oncare24.medication.service.MedicationLogService;
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
@RequestMapping("/api/medications/logs")
@RequiredArgsConstructor
@Tag(name = "MedicationLog", description = "Medication log management")
@SecurityRequirement(name = "BearerAuth")
public class MedicationLogController {

    private final MedicationLogService medicationLogService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create medication log")
    public ApiResponse<MedicationLogResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateMedicationLogRequest request
    ) {
        return ApiResponse.success(medicationLogService.create(userDetails.getUserId(), request));
    }
}
