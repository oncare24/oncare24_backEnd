package com.oncare.oncare24.location.dto;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.location.entity.LocationReportSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationSourcePayload(
        Long wardId,
        BigDecimal latitude,
        BigDecimal longitude,
        Double accuracy,
        LocalDateTime reportedAt,
        String sourceTable,
        Long sourceId,
        ActivityEventType eventType,
        LocationReportSource reportSource
) {
}
