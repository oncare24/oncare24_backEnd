package com.oncare.oncare24.location.dto;

import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.entity.LocationReport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 보호자 화면용 마지막 위치 + 단말 상태 응답.
 * <p>
 * 한 번의 API 호출로 보호자홈 카드에 필요한 정보 전부 반환.
 * 위치 정보가 아예 없는 경우(NEVER_CONNECTED) latitude/longitude/reportedAt이 null.
 */
public record LastLocationResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        Double accuracy,
        LocalDateTime reportedAt,
        DeviceState deviceState,
        LocalDateTime lastReportAt
) {
    public static LastLocationResponse of(LocationReport report, DeviceState state, LocalDateTime lastReportAt) {
        if (report == null) {
            return new LastLocationResponse(null, null, null, null, state, lastReportAt);
        }
        return new LastLocationResponse(
                report.getLatitude(),
                report.getLongitude(),
                report.getAccuracy(),
                report.getCreatedAt(),
                state,
                lastReportAt
        );
    }
}