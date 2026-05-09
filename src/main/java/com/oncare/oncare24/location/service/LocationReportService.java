package com.oncare.oncare24.location.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.service.EncryptedSourceEventService;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.location.dto.LocationReportRequest;
import com.oncare.oncare24.location.dto.LocationReportResponse;
import com.oncare.oncare24.location.entity.DeviceStatus;
import com.oncare.oncare24.location.entity.LocationReport;
import com.oncare.oncare24.location.repository.DeviceStatusRepository;
import com.oncare.oncare24.location.repository.LocationReportRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.location.dto.LastLocationResponse;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 위치 보고 진입 도메인 서비스.
 * <p>
 * <b>처리 순서</b>
 * <ol>
 *     <li>역할 검증 — ELDER만 자기 위치를 보고할 수 있음</li>
 *     <li>정확도 게이트 — accuracy &gt; 100m 면 silent drop (메모리 정책)</li>
 *     <li>LocationReport 저장</li>
 *     <li>DeviceStatus 상태 머신 갱신 (NEVER_CONNECTED/DISCONNECTED → ACTIVE)</li>
 *     <li>GeofencingService 위임 — 같은 트랜잭션</li>
 * </ol>
 *
 * <b>silent drop 정책 근거</b>: Android Location API 문서 권장. accuracy &gt; 100m는
 * 실내/터널/GPS 워밍업 중 부정확한 좌표일 가능성이 높아 지오펜싱에 사용 시
 * 거짓 이탈 알람 유발. 200 OK + stored=false 로 응답하여 클라이언트는 정상 동작 지속.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationReportService {

    private static final double ACCURACY_THRESHOLD_METERS = 100.0;
    private final GuardianWardRepository guardianWardRepository;

    private final LocationReportRepository locationReportRepository;
    private final DeviceStatusRepository deviceStatusRepository;
    private final UserRepository userRepository;
    private final GeofencingService geofencingService;
    private final EncryptedSourceEventService encryptedSourceEventService;

    @Transactional
    public LocationReportResponse report(Long currentUserId, LocationReportRequest req) {
        assertCurrentUserIsElder(currentUserId);

        // 정확도 게이트 — silent drop
        if (req.accuracy() > ACCURACY_THRESHOLD_METERS) {
            log.info("[LOC-DROP] user={}, accuracy={}m exceeds threshold {}m",
                    currentUserId, req.accuracy(), ACCURACY_THRESHOLD_METERS);
            return LocationReportResponse.dropped();
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. 보고 저장
        LocationReport report = LocationReport.builder()
                .userId(currentUserId)
                .latitude(req.latitude())
                .longitude(req.longitude())
                .accuracy(req.accuracy())
                .reportSource(req.reportSource())
                .build();
        LocationReport savedReport = locationReportRepository.save(report);
        encryptedSourceEventService.saveSourceEvent(
                currentUserId,
                ActivityEventType.LOCATION_EVENT,
                "location_report",
                savedReport.getId(),
                now,
                locationReportPayload(savedReport)
        );

        // 2. DeviceStatus 갱신 (없으면 생성 — 회원가입 직후 첫 보고 케이스)
        DeviceStatus device = deviceStatusRepository
                .findByUserId(currentUserId)
                .orElseGet(() -> deviceStatusRepository.save(
                        DeviceStatus.builder().userId(currentUserId).build()
                ));
        device.onLocationReported(now);

        // 3. 지오펜싱 판정 위임
        geofencingService.evaluate(currentUserId, req.latitude(), req.longitude(), now);

        return LocationReportResponse.stored(savedReport.getId(), now);
    }

    private Map<String, Object> locationReportPayload(LocationReport report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("location_report_id", report.getId());
        payload.put("latitude", report.getLatitude());
        payload.put("longitude", report.getLongitude());
        payload.put("accuracy", report.getAccuracy());
        payload.put("report_source", report.getReportSource());
        return payload;
    }

    // ============================================================
    // 검증 헬퍼
    // ============================================================

    private void assertCurrentUserIsElder(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.LOCATION_REPORT_FORBIDDEN);
        }
    }
    /**
     * 특정 ward의 마지막 위치 + 단말 상태.
     * <p>
     * ACCEPTED 보호자만 조회 가능. NEVER_CONNECTED 케이스에는 좌표 null로 반환.
     */
    @Transactional(readOnly = true)
    public LastLocationResponse getLastLocation(Long currentUserId, Long wardId) {
        assertGuardianLinkedToWard(currentUserId, wardId);

        DeviceStatus device = deviceStatusRepository.findByUserId(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_STATUS_NOT_FOUND));

        LocationReport latest = locationReportRepository
                .findFirstByUserIdOrderByCreatedAtDesc(wardId)
                .orElse(null);

        return LastLocationResponse.of(latest, device.getState(), device.getLastReportAt());
    }

    private void assertGuardianLinkedToWard(Long guardianId, Long wardId) {
        boolean linked = guardianWardRepository
                .existsByGuardianIdAndWardIdAndStatus(
                        guardianId, wardId, GuardianWardStatus.ACCEPTED);
        if (!linked) {
            throw new CustomException(ErrorCode.NOT_LINKED_TO_WARD);
        }
    }
}
