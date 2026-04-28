package com.oncare.oncare24.location.dto;

import com.oncare.oncare24.location.entity.LocationReportSource;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * 위치 보고 요청.
 * <p>
 * 프론트가 30분 주기 백그라운드 태스크에서 보내는 페이로드.
 * accuracy 100m 초과는 서버에서 silent drop하지만, 클라이언트는 모든 보고를 일관되게 보냄.
 */
public record LocationReportRequest(

        @NotNull(message = "위도가 필요해요")
        @DecimalMin(value = "-90.0", message = "위도 범위가 올바르지 않아요")
        @DecimalMax(value = "90.0", message = "위도 범위가 올바르지 않아요")
        BigDecimal latitude,

        @NotNull(message = "경도가 필요해요")
        @DecimalMin(value = "-180.0", message = "경도 범위가 올바르지 않아요")
        @DecimalMax(value = "180.0", message = "경도 범위가 올바르지 않아요")
        BigDecimal longitude,

        @NotNull(message = "GPS 정확도가 필요해요")
        @PositiveOrZero(message = "정확도는 0 이상이어야 해요")
        Double accuracy,

        @NotNull(message = "보고 출처가 필요해요")
        LocationReportSource reportSource
) {
}