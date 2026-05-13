package com.oncare.oncare24.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AnalysisStateResponse(
        @Schema(description = "피보호자 ID", example = "2")
        Long wardId,
        @Schema(description = "최신 복약 분석 상태")
        AnalysisStateItemResponse medication,
        @Schema(description = "최신 미활동 분석 상태")
        AnalysisStateItemResponse inactivity
) {
}
