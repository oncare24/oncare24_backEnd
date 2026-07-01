package com.oncare.oncare24.medication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 봉지 이름 변경 요청 (MANUAL 전용). */
public record UpdateGroupNameRequest(
        @Schema(description = "변경할 약 이름", example = "복통약")
        @NotBlank @Size(max = 100) String medicationName
) {
}
