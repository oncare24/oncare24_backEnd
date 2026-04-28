package com.oncare.oncare24.safetyzone.dto;

import com.oncare.oncare24.location.entity.ZoneVisitState;
import com.oncare.oncare24.safetyzone.entity.SafetyZone;
import com.oncare.oncare24.safetyzone.entity.SafetyZoneType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 안전구역 단건 응답.
 * <p>
 * <b>lastVisitedMinutesAgo 계산 규칙</b>
 * <ul>
 *     <li>ZoneVisitState 없음(첫 보고 전 또는 zone 신규 등록 직후): null</li>
 *     <li>현재 INSIDE: 0 (지금 안에 있음)</li>
 *     <li>OUTSIDE이고 lastInsideAt 존재: 현재 시각 - lastInsideAt 의 분 단위</li>
 *     <li>OUTSIDE이고 lastInsideAt 없음(한 번도 안에 들어간 적 없음): null</li>
 * </ul>
 */
public record SafetyZoneResponse(
        Long id,
        Long wardId,
        Long guardianId,
        String name,
        SafetyZoneType type,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer radius,
        boolean notificationEnabled,
        Long lastVisitedMinutesAgo
) {
    public static SafetyZoneResponse from(SafetyZone zone) {
        return from(zone, null);
    }

    public static SafetyZoneResponse from(SafetyZone zone, ZoneVisitState visit) {
        return new SafetyZoneResponse(
                zone.getId(),
                zone.getWardId(),
                zone.getGuardianId(),
                zone.getName(),
                zone.getType(),
                zone.getAddress(),
                zone.getLatitude(),
                zone.getLongitude(),
                zone.getRadius(),
                zone.isNotificationEnabled(),
                computeMinutesAgo(visit)
        );
    }

    private static Long computeMinutesAgo(ZoneVisitState visit) {
        if (visit == null) return null;

        // 현재 INSIDE면 0
        if (visit.getState().name().equals("INSIDE")) return 0L;

        // OUTSIDE이고 lastInsideAt 있으면 분 차이
        if (visit.getLastInsideAt() != null) {
            return Duration.between(visit.getLastInsideAt(), LocalDateTime.now()).toMinutes();
        }

        return null;
    }
}