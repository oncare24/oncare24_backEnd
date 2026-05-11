package com.oncare.oncare24.location.dto;

import com.oncare.oncare24.location.entity.DeviceState;

import java.time.LocalDateTime;

public record DeviceStatusSourceResponse(
        DeviceState deviceStatus,
        LocalDateTime lastActiveAt,
        LocalDateTime disconnectedAt,
        LocalDateTime reportedAt
) {
}
