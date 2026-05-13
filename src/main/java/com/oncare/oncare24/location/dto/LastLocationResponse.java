package com.oncare.oncare24.location.dto;

import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.entity.LocationReport;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 보호자 화면용 마지막 위치 + 단말 상태 응답.
 * <p>
 * 한 번의 API 호출로 보호자홈 카드에 필요한 정보 전부 반환.
 * 위치 정보가 아예 없는 경우(NEVER_CONNECTED) latitude/longitude/reportedAt이 null.
 */
public record LastLocationResponse(
        @Schema(description = "마지막으로 보고된 위도", example = "37.566500")
        BigDecimal latitude,
        @Schema(description = "마지막으로 보고된 경도", example = "126.978000")
        BigDecimal longitude,
        @Schema(description = "GPS 정확도(미터)", example = "18.5")
        Double accuracy,
        @Schema(description = "마지막 위치 보고 일시", example = "2026-05-13T10:00:00")
        LocalDateTime reportedAt,
        @Schema(description = "디바이스 연결 상태", example = "ONLINE")
        DeviceState deviceState,
        @Schema(description = "마지막 보고 수신 일시", example = "2026-05-13T10:00:00")
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
