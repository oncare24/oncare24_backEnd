package com.oncare.oncare24.analysis.dto;

import java.time.LocalDateTime;

public record AnalysisStateItemResponse(
        Integer statusCode,
        String status,
        LocalDateTime analyzedAt
) {
}
