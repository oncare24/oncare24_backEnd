package com.oncare.oncare24.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 4-1 봉지 계층 목록 응답: { "groups": [...] }. */
public record MedicationGroupListResponse(
        @Schema(description = "복용 묶음(DoseGroup) 목록")
        List<MedicationGroupResponse> groups
) {
}
