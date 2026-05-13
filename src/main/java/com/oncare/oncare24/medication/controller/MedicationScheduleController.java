package com.oncare.oncare24.medication.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.medication.dto.CreateMedicationScheduleRequest;
import com.oncare.oncare24.medication.dto.MedicationScheduleResponse;
import com.oncare.oncare24.medication.dto.UpdateMedicationScheduleRequest;
import com.oncare.oncare24.medication.service.MedicationScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/medications/schedules")
@RequiredArgsConstructor
@Tag(name = "MedicationSchedule", description = "복약 일정")
@SecurityRequirement(name = "BearerAuth")
public class MedicationScheduleController {

    private final MedicationScheduleService medicationScheduleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "복약 일정 생성",
            description = "피보호자의 복약 일정을 생성합니다. 생성된 복약 일정 정보는 서버 내부에서 encrypted_activity_log에 암호화 이벤트로 함께 저장됩니다."
    )
    public ApiResponse<MedicationScheduleResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateMedicationScheduleRequest request
    ) {
        return ApiResponse.success(medicationScheduleService.create(userDetails.getUserId(), request));
    }

    @GetMapping
    @Operation(
            summary = "복약 일정 목록 조회",
            description = "피보호자의 복약 일정 목록을 조회합니다."
    )
    public ApiResponse<List<MedicationScheduleResponse>> findAllByWard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 피보호자 ID", example = "2")
            @RequestParam Long wardId
    ) {
        return ApiResponse.success(medicationScheduleService.findAllByWard(userDetails.getUserId(), wardId));
    }

    @GetMapping("/{scheduleId}")
    @Operation(
            summary = "복약 일정 상세 조회",
            description = "복약 일정 ID로 단일 복약 일정을 조회합니다."
    )
    public ApiResponse<MedicationScheduleResponse> findById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 복약 일정 ID", example = "15")
            @PathVariable Long scheduleId
    ) {
        return ApiResponse.success(medicationScheduleService.findById(userDetails.getUserId(), scheduleId));
    }

    @PutMapping("/{scheduleId}")
    @Operation(
            summary = "복약 일정 수정",
            description = "기존 복약 일정을 수정합니다. 수정 이벤트는 서버 내부에서 encrypted_activity_log에 암호화되어 저장됩니다."
    )
    public ApiResponse<MedicationScheduleResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "수정할 복약 일정 ID", example = "15")
            @PathVariable Long scheduleId,
            @Valid @RequestBody UpdateMedicationScheduleRequest request
    ) {
        return ApiResponse.success(medicationScheduleService.update(userDetails.getUserId(), scheduleId, request));
    }

    @DeleteMapping("/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "복약 일정 비활성화",
            description = "복약 일정을 삭제하지 않고 비활성화합니다. 비활성화 이벤트는 서버 내부에서 encrypted_activity_log에 암호화되어 저장됩니다."
    )
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "비활성화할 복약 일정 ID", example = "15")
            @PathVariable Long scheduleId
    ) {
        medicationScheduleService.deactivate(userDetails.getUserId(), scheduleId);
        return ApiResponse.success();
    }
}
