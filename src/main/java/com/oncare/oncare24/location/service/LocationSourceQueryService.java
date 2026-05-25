package com.oncare.oncare24.location.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.repository.EncryptedActivityLogRepository;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.location.dto.DeviceStatusSourcePayload;
import com.oncare.oncare24.location.dto.DeviceStatusSourceResponse;
import com.oncare.oncare24.location.dto.LocationSourcePayload;
import com.oncare.oncare24.location.dto.LocationSourceResponse;
import com.oncare.oncare24.security.crypto.service.CommonCryptoService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationSourceQueryService {

    private static final long DEFAULT_LOOKBACK_HOURS = 24;

    private final EncryptedActivityLogRepository encryptedActivityLogRepository;
    private final CommonCryptoService commonCryptoService;
    private final MlKemKeyProvisionService mlKemKeyProvisionService;
    private final UserRepository userRepository;
    private final GuardianWardRepository guardianWardRepository;

    @Transactional(readOnly = true)
    // 암호화된 위치 보고 원천 조회
    public List<LocationSourceResponse> findLocationRecords(
            Long currentUserId,
            Long wardId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        assertCanAccessWard(currentUserId, wardId);
        TimeRange range = resolveRange(from, to);
        // ward 개인키로 위치 보고 암호화 원천 데이터 복호화 준비
        byte[] wardPrivateKey = mlKemKeyProvisionService.readPrivateKey(wardId);

        return encryptedActivityLogRepository
                .findByWardIdAndEventTypeAndOccurredAtBetweenOrderByOccurredAtDesc(
                        wardId,
                        ActivityEventType.LOCATION_EVENT,
                        range.from(),
                        range.to()
                )
                .stream()
                // encrypted_activity_log의 위치 보고 이벤트를 복호화
                .map(log -> decryptLocationRecord(log, wardPrivateKey))
                .sorted(Comparator
                        .comparing(
                                LocationRecord::sortAt,
                                Comparator.nullsLast(LocalDateTime::compareTo)
                        ))
                .map(LocationRecord::response)
                .toList();
    }

    @Transactional(readOnly = true)
    // 암호화된 기기 상태 원천 조회
    public List<DeviceStatusSourceResponse> findDeviceStatusRecords(
            Long currentUserId,
            Long wardId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        assertCanAccessWard(currentUserId, wardId);
        TimeRange range = resolveRange(from, to);
        // ward 개인키로 기기 상태 암호화 원천 데이터 복호화 준비
        byte[] wardPrivateKey = mlKemKeyProvisionService.readPrivateKey(wardId);

        return encryptedActivityLogRepository
                .findByWardIdAndEventTypeAndOccurredAtBetweenOrderByOccurredAtDesc(
                        wardId,
                        ActivityEventType.DEVICE_EVENT,
                        range.from(),
                        range.to()
                )
                .stream()
                // encrypted_activity_log의 기기 상태 이벤트를 복호화
                .map(log -> decryptDeviceStatusRecord(log, wardPrivateKey))
                .sorted(Comparator
                        .comparing(
                                DeviceStatusRecord::sortAt,
                                Comparator.nullsLast(LocalDateTime::compareTo)
                        ))
                .map(DeviceStatusRecord::response)
                .toList();
    }

    // 위치 보고 암호화 이벤트 복호화
    private LocationRecord decryptLocationRecord(EncryptedActivityLog log, byte[] wardPrivateKey) {
        // 암호화된 위치 보고 payload 복호화
        LocationSourcePayload payload = decryptActivityPayload(log, wardPrivateKey, LocationSourcePayload.class);
        LocalDateTime reportedAt = payload.reportedAt() != null ? payload.reportedAt() : log.getOccurredAt();
        return new LocationRecord(
                new LocationSourceResponse(
                        payload.latitude(),
                        payload.longitude(),
                        payload.accuracy(),
                        reportedAt,
                        payload.reportSource()
                ),
                reportedAt
        );
    }

    // 기기 상태 암호화 이벤트 복호화
    private DeviceStatusRecord decryptDeviceStatusRecord(EncryptedActivityLog log, byte[] wardPrivateKey) {
        // 암호화된 기기 상태 payload 복호화
        DeviceStatusSourcePayload payload = decryptActivityPayload(log, wardPrivateKey, DeviceStatusSourcePayload.class);
        LocalDateTime reportedAt = payload.reportedAt() != null ? payload.reportedAt() : log.getOccurredAt();
        return new DeviceStatusRecord(
                new DeviceStatusSourceResponse(
                        payload.deviceStatus(),
                        payload.lastActiveAt(),
                        payload.disconnectedAt(),
                        reportedAt
                ),
                reportedAt
        );
    }

    // 위치 기기 원천 payload 공통 복호화
    private <T> T decryptActivityPayload(EncryptedActivityLog log, byte[] wardPrivateKey, Class<T> payloadType) {
        // encrypted_activity_log payload를 CommonCryptoService로 복호화
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

    private TimeRange resolveRange(LocalDateTime from, LocalDateTime to) {
        LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();
        LocalDateTime resolvedFrom = from != null ? from : resolvedTo.minusHours(DEFAULT_LOOKBACK_HOURS);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "from must be before or equal to to.");
        }
        return new TimeRange(resolvedFrom, resolvedTo);
    }

    private void assertCanAccessWard(Long currentUserId, Long wardId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ELDER));

        if (ward.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.INVALID_ELDER);
        }

        if (currentUser.getRole() == UserRole.ELDER && currentUserId.equals(wardId)) {
            return;
        }

        if (currentUser.getRole() == UserRole.GUARDIAN
                && guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                currentUserId,
                wardId,
                GuardianWardStatus.ACCEPTED
        )) {
            return;
        }

        throw new CustomException(ErrorCode.NOT_LINKED_TO_WARD);
    }

    private record TimeRange(LocalDateTime from, LocalDateTime to) {
    }

    private record LocationRecord(LocationSourceResponse response, LocalDateTime sortAt) {
    }

    private record DeviceStatusRecord(DeviceStatusSourceResponse response, LocalDateTime sortAt) {
    }
}
