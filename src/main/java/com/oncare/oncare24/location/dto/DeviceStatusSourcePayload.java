package com.oncare.oncare24.location.dto;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.location.entity.DeviceState;

import java.time.LocalDateTime;

public record DeviceStatusSourcePayload(
        Long wardId,
        DeviceState deviceStatus,
        LocalDateTime lastActiveAt,
        LocalDateTime disconnectedAt,
        LocalDateTime reportedAt,
        String sourceTable,
        Long sourceId,
        ActivityEventType eventType
) {
}
