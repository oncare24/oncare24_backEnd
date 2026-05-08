package com.oncare.oncare24.inactivity.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.inactivity.dto.InactivityAnalysisResult;
import com.oncare.oncare24.inactivity.entity.InactivityAnalysisStatus;
import com.oncare.oncare24.inactivity.entity.InactivityDetectionRule;
import com.oncare.oncare24.inactivity.repository.InactivityDetectionRuleRepository;
import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.entity.DeviceStatus;
import com.oncare.oncare24.location.entity.LocationReport;
import com.oncare.oncare24.location.repository.DeviceStatusRepository;
import com.oncare.oncare24.location.repository.LocationReportRepository;
import com.oncare.oncare24.location.util.Haversine;
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
    private final LocationReportRepository locationReportRepository;
    private final DeviceStatusRepository deviceStatusRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public InactivityAnalysisResult analyzeWardInactivity(Long wardId, LocalDateTime analysisAt) {
        assertWardIsElder(wardId);

        InactivityDetectionRule rule = inactivityDetectionRuleRepository.findByWardIdAndActiveTrue(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        return analyzeRule(rule, analysisAt);
    }

    @Transactional(readOnly = true)
    public List<InactivityAnalysisResult> analyzeAllActiveWardInactivity(LocalDateTime analysisAt) {
        return inactivityDetectionRuleRepository.findByActiveTrueOrderByWardIdAsc()
                .stream()
                .map(rule -> analyzeRule(rule, analysisAt))
                .toList();
    }

    private InactivityAnalysisResult analyzeRule(InactivityDetectionRule rule, LocalDateTime analysisAt) {
        Long wardId = rule.getWardId();

        Optional<DeviceStatus> deviceStatus = deviceStatusRepository.findByUserId(wardId);
        if (deviceStatus.map(DeviceStatus::getState).filter(DeviceState.DISCONNECTED::equals).isPresent()) {
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

        Optional<LocationReport> latestReport = locationReportRepository
                .findFirstByUserIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(wardId, analysisAt);
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

        LocalDateTime lastReportAt = latestReport.get().getCreatedAt();
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
        List<LocationReport> reports = locationReportRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(wardId, searchFrom, analysisAt);
        List<LocationReport> reliableReports = reports.stream()
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
                .orElse(reliableReports.get(0).getCreatedAt());
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
            List<LocationReport> reliableReports,
            InactivityDetectionRule rule
    ) {
        LocalDateTime lastMovementAt = null;

        for (int i = 1; i < reliableReports.size(); i++) {
            LocationReport previous = reliableReports.get(i - 1);
            LocationReport current = reliableReports.get(i);

            double distanceMeters = Haversine.distance(
                    previous.getLatitude(),
                    previous.getLongitude(),
                    current.getLatitude(),
                    current.getLongitude()
            );
            double movementThreshold = previous.getAccuracy()
                    + current.getAccuracy()
                    + rule.getMinMovementMeters();

            if (distanceMeters > movementThreshold) {
                lastMovementAt = current.getCreatedAt();
            }
        }

        return Optional.ofNullable(lastMovementAt);
    }

    private boolean isReliableAccuracy(LocationReport report, InactivityDetectionRule rule) {
        return report.getAccuracy() != null && report.getAccuracy() <= rule.getMaxAccuracyMeters();
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
}
