package com.oncare.oncare24.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record AnalysisStateItemResponse(
        @Schema(description = "분석 상태 코드", example = "0")
        Integer statusCode,
        @Schema(description = "분석 상태명", example = "NORMAL")
        String status,
        @Schema(description = "분석된 일시", example = "2026-05-13T10:00:00")
        LocalDateTime analyzedAt
) {
}
