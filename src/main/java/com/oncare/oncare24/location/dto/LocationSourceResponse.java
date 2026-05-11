package com.oncare.oncare24.location.dto;

import com.oncare.oncare24.location.entity.LocationReportSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationSourceResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        Double accuracy,
        LocalDateTime reportedAt,
        LocationReportSource reportSource
) {
}
