package com.oncare.oncare24.analysis.dto;

public record AnalysisStateResponse(
        Long wardId,
        AnalysisStateItemResponse medication,
        AnalysisStateItemResponse inactivity
) {
}
