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
@Tag(name = "MedicationLog", description = "복약 기록")
@SecurityRequirement(name = "BearerAuth")
public class MedicationLogController {

    private final MedicationLogService medicationLogService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "복약 기록 생성",
            description = "피보호자의 실제 복약 완료 기록을 저장합니다. 복약 기록은 서버 내부에서 encrypted_activity_log에 암호화 이벤트로 함께 저장됩니다."
    )
    public ApiResponse<MedicationLogResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateMedicationLogRequest request
    ) {
        return ApiResponse.success(medicationLogService.create(userDetails.getUserId(), request));
    }
}
