package com.oncare.oncare24.hospital.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.hospital.dto.RecommendRequest;
import com.oncare.oncare24.location.entity.LocationReport;
import com.oncare.oncare24.location.repository.LocationReportRepository;
import com.oncare.oncare24.safetyzone.entity.SafetyZone;
import com.oncare.oncare24.safetyzone.repository.SafetyZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 위치 폴백 체인 결정자.
 * <p>
 * 우선순위:
 * <ol>
 *     <li>요청 본문의 lat/lon</li>
 *     <li>최근 5분 내 LocationReport (마지막 위치 보고)</li>
 *     <li>해당 사용자가 wardId인 활성 안전구역의 첫 번째 (보통 "집")</li>
 *     <li>모두 실패 → CustomException(LOCATION_NOT_FOUND)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLocationResolver {

    private static final Duration RECENT_REPORT_THRESHOLD = Duration.ofMinutes(5);

    private final LocationReportRepository locationReportRepository;
    private final SafetyZoneRepository safetyZoneRepository;

    public record ResolvedLocation(double latitude, double longitude, String source) {}

    @Transactional(readOnly = true)
    public ResolvedLocation resolve(Long userId, RecommendRequest request) {

        // 1. 요청에 위치가 있으면 그대로 사용
        if (request.hasLocation()) {
            log.debug("[Location] using request lat/lon");
            return new ResolvedLocation(request.latitude(), request.longitude(), "REQUEST");
        }

        // 2. 최근 5분 내 보고된 위치
        Optional<LocationReport> recent = locationReportRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (recent.isPresent() && isRecent(recent.get())) {
            LocationReport r = recent.get();
            log.debug("[Location] using recent location report from {}", r.getCreatedAt());
            return new ResolvedLocation(
                    r.getLatitude().doubleValue(),
                    r.getLongitude().doubleValue(),
                    "RECENT_REPORT"
            );
        }

        // 3. 안전구역 첫 번째 중심점
        List<SafetyZone> zones = safetyZoneRepository.findByWardIdAndActiveTrueOrderByCreatedAtAsc(userId);
        if (!zones.isEmpty()) {
            SafetyZone first = zones.get(0);
            log.debug("[Location] using first safety zone: {}", first.getName());
            return new ResolvedLocation(
                    first.getLatitude().doubleValue(),
                    first.getLongitude().doubleValue(),
                    "SAFETY_ZONE"
            );
        }

        // 4. 모두 실패
        log.warn("[Location] no location available for userId={}", userId);
        throw new CustomException(ErrorCode.LOCATION_NOT_FOUND);
    }

    private boolean isRecent(LocationReport report) {
        return Duration.between(report.getCreatedAt(), LocalDateTime.now())
                .compareTo(RECENT_REPORT_THRESHOLD) < 0;
    }
}
