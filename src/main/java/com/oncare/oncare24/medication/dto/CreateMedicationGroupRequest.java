package com.oncare.oncare24.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 4-5 수동 봉지(약) 생성 요청. */
public record CreateMedicationGroupRequest(
        @Schema(description = "약 이름(수동)", example = "복통약")
        @NotBlank String medicationName,
        @Schema(description = "시각(봉지) 목록")
        @NotEmpty List<MedicationPacketCreateRequest> packets
) {
}
