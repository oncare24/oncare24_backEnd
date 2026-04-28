package com.oncare.oncare24.location.dto;

import java.time.LocalDateTime;

/**
 * 위치 보고 결과.
 * <p>
 * stored=false인 경우 accuracy threshold 초과로 silent drop된 케이스. 클라이언트는 200 OK로 받지만
 * 디버깅 로그 등에 활용 가능. 정상 케이스는 stored=true와 reportId, reportedAt 반환.
 */
public record LocationReportResponse(
        boolean stored,
        Long reportId,
        LocalDateTime reportedAt
) {
    public static LocationReportResponse stored(Long reportId, LocalDateTime reportedAt) {
        return new LocationReportResponse(true, reportId, reportedAt);
    }

    public static LocationReportResponse dropped() {
        return new LocationReportResponse(false, null, null);
    }
}