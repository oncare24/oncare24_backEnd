package com.oncare.oncare24.sos.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * SOS 호출 요청.
 * <p>
 * 모든 필드 옵션. 위치 권한이 없거나 GPS 실패한 케이스에도 일단 호출은 받음 —
 * 위치 정보가 없다고 SOS를 막으면 본질에 어긋남.
 *
 * <b>accuracy 처리</b>: 100m 초과면 서버에서 좌표 자체를 무시하고 FALLBACK으로 처리.
 * (LocationReportService와 동일한 정책)
 */
@Schema(description = "SOS 긴급 호출 요청")
public record SosTriggerRequest(
        @Schema(description = "호출 시점 위도 (-90.0 ~ 90.0). 없으면 서버가 최신 위치 보고로 폴백.", example = "35.3350000")
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
        BigDecimal latitude,

        @Schema(description = "호출 시점 경도 (-180.0 ~ 180.0).", example = "129.0386000")
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
        BigDecimal longitude,

        @Schema(description = "GPS 정확도 (미터). 100 초과면 좌표 무시하고 폴백.", example = "12.5")
        Double accuracy
) {
}