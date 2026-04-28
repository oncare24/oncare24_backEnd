package com.oncare.oncare24.guardian.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.dto.WardResponse;
import com.oncare.oncare24.guardian.dto.WardStatus;
import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.entity.DeviceStatus;
import com.oncare.oncare24.location.entity.ZoneState;
import com.oncare.oncare24.location.entity.ZoneVisitState;
import com.oncare.oncare24.location.repository.DeviceStatusRepository;
import com.oncare.oncare24.location.repository.ZoneVisitStateRepository;
import com.oncare.oncare24.safetyzone.entity.SafetyZone;
import com.oncare.oncare24.safetyzone.repository.SafetyZoneRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 보호자 시점 — 내 피보호자 목록 조회 서비스.
 * <p>
 * 단일 책임: ACCEPTED 매칭을 가져와서 "보호자 홈에 한 카드로 그릴 수 있는 형태"로 가공.
 * Step 8 도메인(device_status, zone_visit_states, safety_zones) 모두 읽기만 함.
 */
@Service
@RequiredArgsConstructor
public class WardService {

    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;
    private final DeviceStatusRepository deviceStatusRepository;
    private final ZoneVisitStateRepository zoneVisitStateRepository;
    private final SafetyZoneRepository safetyZoneRepository;

    @Transactional(readOnly = true)
    public List<WardResponse> findMyWards(Long currentUserId) {
        assertCurrentUserIsGuardian(currentUserId);

        List<GuardianWard> links = guardianWardRepository
                .findByGuardianIdAndStatusOrderByCreatedAtDesc(currentUserId, GuardianWardStatus.ACCEPTED);

        if (links.isEmpty()) return List.of();

        // ward User 정보 batch
        List<Long> wardIds = links.stream().map(GuardianWard::getWardId).toList();
        Map<Long, User> wardMap = new HashMap<>();
        userRepository.findAllById(wardIds).forEach(u -> wardMap.put(u.getId(), u));

        // ward별 enrichment
        return links.stream()
                .map(link -> {
                    User ward = wardMap.get(link.getWardId());
                    if (ward == null) {
                        // 정합성 깨진 link (ward 계정 삭제됨) — 건너뛰지 않고 최소 정보로 표시
                        return null;
                    }
                    StatusBundle bundle = determineStatus(ward.getId());
                    return WardResponse.of(
                            link,
                            ward,
                            bundle.status,
                            bundle.locationLabel,
                            bundle.lastReportedMinutesAgo
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ============================================================
    // 상태 결정 로직 (위 결정 트리의 코드화)
    // ============================================================

    private StatusBundle determineStatus(Long wardId) {
        DeviceStatus device = deviceStatusRepository.findByUserId(wardId).orElse(null);

        // 1. 단말 정보 없거나 한 번도 보고 없음
        if (device == null || device.getState() == DeviceState.NEVER_CONNECTED) {
            return new StatusBundle(WardStatus.UNKNOWN, "위치 정보 없음", null);
        }

        Long minutesAgo = computeMinutesAgo(device.getLastReportAt());

        // 2. DISCONNECTED — 끊긴 상태
        if (device.getState() == DeviceState.DISCONNECTED) {
            return new StatusBundle(WardStatus.DISCONNECTED, "마지막 위치 확인 중", minutesAgo);
        }

        // 3. ACTIVE — 안전구역 평가
        List<SafetyZone> zones = safetyZoneRepository
                .findByWardIdAndActiveTrueOrderByCreatedAtAsc(wardId);

        if (zones.isEmpty()) {
            return new StatusBundle(WardStatus.UNKNOWN, "안전구역 미설정", minutesAgo);
        }

        // zone_id별 visit state 한 번에 매핑
        Map<Long, ZoneVisitState> visitMap = new HashMap<>();
        zoneVisitStateRepository.findByWardId(wardId)
                .forEach(v -> visitMap.put(v.getZoneId(), v));

        // 4. OUTSIDE 우선
        boolean anyOutside = zones.stream().anyMatch(z -> {
            ZoneVisitState v = visitMap.get(z.getId());
            return v != null && v.getState() == ZoneState.OUTSIDE;
        });
        if (anyOutside) {
            return new StatusBundle(WardStatus.OUTSIDE, "안전구역 외부", minutesAgo);
        }

        // 5. INSIDE인 zone 첫 매치 → 그 이름 사용
        SafetyZone insideZone = zones.stream()
                .filter(z -> {
                    ZoneVisitState v = visitMap.get(z.getId());
                    return v != null && v.getState() == ZoneState.INSIDE;
                })
                .findFirst()
                .orElse(null);

        if (insideZone != null) {
            return new StatusBundle(WardStatus.INSIDE, insideZone.getName() + " 근처", minutesAgo);
        }

        // 6. 모든 zone UNKNOWN — ACTIVE이지만 첫 보고 직후 등
        return new StatusBundle(WardStatus.UNKNOWN, "위치 확인 중", minutesAgo);
    }

    private Long computeMinutesAgo(LocalDateTime lastReportAt) {
        if (lastReportAt == null) return null;
        long minutes = Duration.between(lastReportAt, LocalDateTime.now()).toMinutes();
        return Math.max(0, minutes);
    }

    // === 검증 헬퍼 ===

    private void assertCurrentUserIsGuardian(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.GUARDIAN) {
            throw new CustomException(ErrorCode.ROLE_NOT_GUARDIAN);
        }
    }

    /** 상태 결정 결과를 묶어 옮기기 위한 내부 record. 외부로 노출 X. */
    private record StatusBundle(WardStatus status, String locationLabel, Long lastReportedMinutesAgo) {
    }
}