package com.oncare.oncare24.inactivity.dto;

import com.oncare.oncare24.inactivity.entity.InactivityAnalysisStatus;

import java.time.LocalDateTime;

public record InactivityAnalysisResult(
        Long wardId,
        Long ruleId,
        LocalDateTime analysisAt,
        InactivityAnalysisStatus status,
        LocalDateTime lastLocationReportAt,
        LocalDateTime lastReliableMovementAt,
        Long inactiveMinutes,
        Long staleLocationMinutes,
        Integer usedReportCount,
        Integer reliableReportCount,
        String detailMessage
) {
}
