package com.oncare.oncare24.medication.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.medication.dto.CreateMedicationScheduleRequest;
import com.oncare.oncare24.medication.dto.MedicationScheduleResponse;
import com.oncare.oncare24.medication.dto.UpdateMedicationScheduleRequest;
import com.oncare.oncare24.medication.service.MedicationScheduleService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "MedicationSchedule", description = "Medication schedule management")
@SecurityRequirement(name = "BearerAuth")
public class MedicationScheduleController {

    private final MedicationScheduleService medicationScheduleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create medication schedule")
    public ApiResponse<MedicationScheduleResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateMedicationScheduleRequest request
    ) {
        return ApiResponse.success(medicationScheduleService.create(userDetails.getUserId(), request));
    }

    @GetMapping
    @Operation(summary = "Find medication schedules by ward")
    public ApiResponse<List<MedicationScheduleResponse>> findAllByWard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long wardId
    ) {
        return ApiResponse.success(medicationScheduleService.findAllByWard(userDetails.getUserId(), wardId));
    }

    @GetMapping("/{scheduleId}")
    @Operation(summary = "Find medication schedule")
    public ApiResponse<MedicationScheduleResponse> findById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long scheduleId
    ) {
        return ApiResponse.success(medicationScheduleService.findById(userDetails.getUserId(), scheduleId));
    }

    @PutMapping("/{scheduleId}")
    @Operation(summary = "Update medication schedule")
    public ApiResponse<MedicationScheduleResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long scheduleId,
            @Valid @RequestBody UpdateMedicationScheduleRequest request
    ) {
        return ApiResponse.success(medicationScheduleService.update(userDetails.getUserId(), scheduleId, request));
    }

    @DeleteMapping("/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate medication schedule")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long scheduleId
    ) {
        medicationScheduleService.deactivate(userDetails.getUserId(), scheduleId);
        return ApiResponse.success();
    }
}
