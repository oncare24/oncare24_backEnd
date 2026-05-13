package com.oncare.oncare24.location.dto;

import com.oncare.oncare24.location.entity.LocationReportSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationSourceResponse(
        @Schema(description = "복호화된 위도", example = "37.566500")
        BigDecimal latitude,
        @Schema(description = "복호화된 경도", example = "126.978000")
        BigDecimal longitude,
        @Schema(description = "GPS 정확도(미터)", example = "18.5")
        Double accuracy,
        @Schema(description = "위치 보고 일시", example = "2026-05-13T10:00:00")
        LocalDateTime reportedAt,
        @Schema(description = "위치 보고 출처", example = "BACKGROUND")
        LocationReportSource reportSource
) {
}
