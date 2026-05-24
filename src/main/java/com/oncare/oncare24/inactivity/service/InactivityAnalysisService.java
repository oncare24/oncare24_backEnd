package com.oncare.oncare24.inactivity.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.AnalysisType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.entity.WardAnalysisState;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.analysis.repository.WardAnalysisStateRepository;
import com.oncare.oncare24.analysis.service.AnalysisStateService;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.inactivity.dto.InactivityAnalysisResult;
import com.oncare.oncare24.inactivity.entity.InactivityAnalysisStatus;
import com.oncare.oncare24.inactivity.entity.InactivityDetectionRule;
import com.oncare.oncare24.inactivity.repository.InactivityDetectionRuleRepository;
import com.oncare.oncare24.notification.service.NotificationService;
import com.oncare.oncare24.location.dto.DeviceStatusSourcePayload;
import com.oncare.oncare24.location.dto.LocationSourcePayload;
import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.util.Haversine;
import com.oncare.oncare24.security.crypto.service.CommonCryptoService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InactivityAnalysisService {

    private final InactivityDetectionRuleRepository inactivityDetectionRuleRepository;
    private final EncryptedActivityLogRepository encryptedActivityLogRepository;
    private final CommonCryptoService commonCryptoService;
    private final MlKemKeyProvisionService mlKemKeyProvisionService;
    private final UserRepository userRepository;
    private final AnalysisStateService analysisStateService;
    private final WardAnalysisStateRepository wardAnalysisStateRepository;
    private final NotificationService notificationService;

    // 분석 결과 상태코드 (WardAnalysisState.statusCode 규약과 동일)
    private static final int STATUS_CODE_INACTIVE = 1;   // 활동 이상 감지
    private static final int STATUS_CODE_NONE = -1;      // 분석 이력 없음 (전이 판정용 sentinel)

    @Transactional(readOnly = true)
    public InactivityAnalysisResult analyzeWardInactivity(Long wardId, LocalDateTime analysisAt) {
        assertWardIsElder(wardId);

        InactivityDetectionRule rule = inactivityDetectionRuleRepository.findByWardIdAndActiveTrue(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        InactivityAnalysisResult result = analyzeRule(rule, analysisAt);
        persistInactivityState(result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<InactivityAnalysisResult> analyzeAllActiveWardInactivity(LocalDateTime analysisAt) {
        List<InactivityAnalysisResult> results = inactivityDetectionRuleRepository.findByActiveTrueOrderByWardIdAsc()
                .stream()
                .map(rule -> analyzeRule(rule, analysisAt))
                .toList();
        results.forEach(this::persistInactivityState);
        return results;
    }

    private InactivityAnalysisResult analyzeRule(InactivityDetectionRule rule, LocalDateTime analysisAt) {
        Long wardId = rule.getWardId();
        byte[] wardPrivateKey = mlKemKeyProvisionService.readPrivateKey(wardId);

        Optional<DeviceStatusSourcePayload> deviceStatus = findLatestDeviceStatus(wardId, analysisAt, wardPrivateKey);
        if (deviceStatus.map(DeviceStatusSourcePayload::deviceStatus).filter(DeviceState.DISCONNECTED::equals).isPresent()) {
            return result(
                    rule,
                    analysisAt,
                    InactivityAnalysisStatus.DEVICE_DISCONNECTED,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    "Device is disconnected, so inactivity analysis is deferred."
            );
        }

        Optional<DecryptedLocationReport> latestReport = findLatestLocationReport(wardId, analysisAt, wardPrivateKey);
        if (latestReport.isEmpty()) {
            return result(
                    rule,
                    analysisAt,
                    InactivityAnalysisStatus.STALE_LOCATION_DANGER,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    "No location report exists for this ward."
            );
        }

        LocalDateTime lastReportAt = latestReport.get().reportedAt();
        long staleLocationMinutes = minutesBetween(lastReportAt, analysisAt);
        if (staleLocationMinutes >= rule.getStaleLocationDangerMinutes()) {
            return result(
                    rule,
                    analysisAt,
                    InactivityAnalysisStatus.STALE_LOCATION_DANGER,
                    lastReportAt,
                    null,
                    null,
                    staleLocationMinutes,
                    0,
                    0,
                    "Latest location report exceeded stale danger threshold."
            );
        }
        if (staleLocationMinutes >= rule.getStaleLocationWarningMinutes()) {
            return result(
                    rule,
                    analysisAt,
                    InactivityAnalysisStatus.STALE_LOCATION_WARNING,
                    lastReportAt,
                    null,
                    null,
                    staleLocationMinutes,
                    0,
                    0,
                    "Latest location report exceeded stale warning threshold."
            );
        }

        LocalDateTime searchFrom = analysisAt.minusMinutes(resolveLookbackMinutes(rule));
        List<DecryptedLocationReport> reports = findLocationReports(wardId, searchFrom, analysisAt, wardPrivateKey);
        List<DecryptedLocationReport> reliableReports = reports.stream()
                .filter(report -> isReliableAccuracy(report, rule))
                .toList();

        if (reports.isEmpty() || reliableReports.isEmpty()) {
            return result(
                    rule,
                    analysisAt,
                    InactivityAnalysisStatus.LOCATION_UNRELIABLE,
                    lastReportAt,
                    null,
                    null,
                    staleLocationMinutes,
                    reports.size(),
                    reliableReports.size(),
                    "No reliable location reports are available in the analysis window."
            );
        }

        LocalDateTime lastReliableMovementAt = findLastReliableMovementAt(reliableReports, rule)
                .orElse(reliableReports.get(0).reportedAt());
        long inactiveMinutes = minutesBetween(lastReliableMovementAt, analysisAt);
        InactivityAnalysisStatus status = determineInactiveStatus(inactiveMinutes, rule);

        return result(
                rule,
                analysisAt,
                status,
                lastReportAt,
                lastReliableMovementAt,
                inactiveMinutes,
                staleLocationMinutes,
                reports.size(),
                reliableReports.size(),
                buildDetailMessage(status, rule)
        );
    }

    private Optional<LocalDateTime> findLastReliableMovementAt(
            List<DecryptedLocationReport> reliableReports,
            InactivityDetectionRule rule
    ) {
        LocalDateTime lastMovementAt = null;

        for (int i = 1; i < reliableReports.size(); i++) {
            DecryptedLocationReport previous = reliableReports.get(i - 1);
            DecryptedLocationReport current = reliableReports.get(i);

            double distanceMeters = Haversine.distance(
                    previous.latitude(),
                    previous.longitude(),
                    current.latitude(),
                    current.longitude()
            );
            double movementThreshold = previous.accuracy()
                    + current.accuracy()
                    + rule.getMinMovementMeters();

            if (distanceMeters > movementThreshold) {
                lastMovementAt = current.reportedAt();
            }
        }

        return Optional.ofNullable(lastMovementAt);
    }

    private boolean isReliableAccuracy(DecryptedLocationReport report, InactivityDetectionRule rule) {
        return report.accuracy() != null && report.accuracy() <= rule.getMaxAccuracyMeters();
    }

    private Optional<DeviceStatusSourcePayload> findLatestDeviceStatus(
            Long wardId,
            LocalDateTime analysisAt,
            byte[] wardPrivateKey
    ) {
        return encryptedActivityLogRepository
                .findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                        wardId,
                        ActivityEventType.DEVICE_EVENT,
                        "device_status",
                        analysisAt
                )
                .map(log -> decryptActivityPayload(log, wardPrivateKey, DeviceStatusSourcePayload.class));
    }

    private Optional<DecryptedLocationReport> findLatestLocationReport(
            Long wardId,
            LocalDateTime analysisAt,
            byte[] wardPrivateKey
    ) {
        return encryptedActivityLogRepository
                .findFirstByWardIdAndEventTypeAndSourceTableAndOccurredAtLessThanEqualOrderByOccurredAtDesc(
                        wardId,
                        ActivityEventType.LOCATION_EVENT,
                        "location_report",
                        analysisAt
                )
                .map(log -> decryptLocationReport(log, wardPrivateKey));
    }

    private List<DecryptedLocationReport> findLocationReports(
            Long wardId,
            LocalDateTime from,
            LocalDateTime to,
            byte[] wardPrivateKey
    ) {
        return encryptedActivityLogRepository
                .findByWardIdAndEventTypeAndSourceTableAndOccurredAtBetweenOrderByOccurredAtAsc(
                        wardId,
                        ActivityEventType.LOCATION_EVENT,
                        "location_report",
                        from,
                        to
                )
                .stream()
                .map(log -> decryptLocationReport(log, wardPrivateKey))
                .toList();
    }

    private DecryptedLocationReport decryptLocationReport(EncryptedActivityLog log, byte[] wardPrivateKey) {
        LocationSourcePayload payload = decryptActivityPayload(log, wardPrivateKey, LocationSourcePayload.class);
        LocalDateTime reportedAt = payload.reportedAt() != null ? payload.reportedAt() : log.getOccurredAt();
        return new DecryptedLocationReport(
                payload.latitude(),
                payload.longitude(),
                payload.accuracy(),
                reportedAt
        );
    }

    private <T> T decryptActivityPayload(EncryptedActivityLog log, byte[] wardPrivateKey, Class<T> payloadType) {
        return commonCryptoService.decryptActivityLogPayload(
                log.getDataKeyId(),
                log.getEncryptedPackage(),
                log.getAadJson(),
                log.getWardId(),
                CommonCryptoService.OWNER_TYPE_USER,
                wardPrivateKey,
                payloadType
        );
    }

    private InactivityAnalysisStatus determineInactiveStatus(long inactiveMinutes, InactivityDetectionRule rule) {
        if (inactiveMinutes >= rule.getDangerMinutes()) {
            return InactivityAnalysisStatus.INACTIVE_DANGER;
        }
        if (inactiveMinutes >= rule.getWarningMinutes()) {
            return InactivityAnalysisStatus.INACTIVE_WARNING;
        }
        return InactivityAnalysisStatus.NORMAL;
    }

    private void persistInactivityState(InactivityAnalysisResult result) {
        int newStatusCode = inactivityStatusCode(result.status());

        // upsert 전에 직전 상태코드를 읽어둔다 — 정상↔이상 "전이" 판정용.
        int previousStatusCode = wardAnalysisStateRepository
                .findByWardIdAndAnalysisType(result.wardId(), AnalysisType.INACTIVITY)
                .map(WardAnalysisState::getStatusCode)
                .orElse(STATUS_CODE_NONE);

        analysisStateService.upsertLatestState(
                result.wardId(),
                AnalysisType.INACTIVITY,
                newStatusCode,
                result.analysisAt()
        );

        // 정상/위치불명/이력없음 → 이상(1) 으로 전이된 순간에만 1회 FCM 발송.
        // 이상이 지속되는 동안 위치 이벤트마다 재분석돼도 재발송하지 않음 (알림 스팸 방지).
        if (previousStatusCode != STATUS_CODE_INACTIVE && newStatusCode == STATUS_CODE_INACTIVE) {
            boolean danger = result.status() == InactivityAnalysisStatus.INACTIVE_DANGER;
            notificationService.notifyInactivityDetected(result.wardId(), danger);
        }
    }

    private int inactivityStatusCode(InactivityAnalysisStatus status) {
        return switch (status) {
            case NORMAL -> 0;
            case INACTIVE_WARNING, INACTIVE_DANGER -> 1;
            case STALE_LOCATION_WARNING, STALE_LOCATION_DANGER, LOCATION_UNRELIABLE, DEVICE_DISCONNECTED -> 2;
        };
    }

    private long resolveLookbackMinutes(InactivityDetectionRule rule) {
        return Math.max(rule.getDangerMinutes(), rule.getStaleLocationDangerMinutes());
    }

    private long minutesBetween(LocalDateTime from, LocalDateTime to) {
        return Math.max(0, Duration.between(from, to).toMinutes());
    }

    private String buildDetailMessage(InactivityAnalysisStatus status, InactivityDetectionRule rule) {
        String intervalSuffix = " Expected report interval is "
                + rule.getExpectedReportIntervalMinutes()
                + " minutes.";
        return switch (status) {
            case NORMAL -> "Reliable movement was detected within the normal window." + intervalSuffix;
            case INACTIVE_WARNING -> "No reliable movement was detected beyond the warning threshold." + intervalSuffix;
            case INACTIVE_DANGER -> "No reliable movement was detected beyond the danger threshold." + intervalSuffix;
            case STALE_LOCATION_WARNING -> "Location reports are stale beyond the warning threshold." + intervalSuffix;
            case STALE_LOCATION_DANGER -> "Location reports are stale beyond the danger threshold." + intervalSuffix;
            case LOCATION_UNRELIABLE -> "Location reports exist but accuracy is too low for reliable analysis." + intervalSuffix;
            case DEVICE_DISCONNECTED -> "Device is disconnected." + intervalSuffix;
        };
    }

    private InactivityAnalysisResult result(
            InactivityDetectionRule rule,
            LocalDateTime analysisAt,
            InactivityAnalysisStatus status,
            LocalDateTime lastLocationReportAt,
            LocalDateTime lastReliableMovementAt,
            Long inactiveMinutes,
            Long staleLocationMinutes,
            int usedReportCount,
            int reliableReportCount,
            String detailMessage
    ) {
        return new InactivityAnalysisResult(
                rule.getWardId(),
                rule.getId(),
                analysisAt,
                status,
                lastLocationReportAt,
                lastReliableMovementAt,
                inactiveMinutes,
                staleLocationMinutes,
                usedReportCount,
                reliableReportCount,
                detailMessage
        );
    }

    private void assertWardIsElder(Long wardId) {
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (ward.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.INVALID_ELDER);
        }
    }

    private record DecryptedLocationReport(
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            Double accuracy,
            LocalDateTime reportedAt
    ) {
    }
}
