package com.oncare.oncare24.safetyzone.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.safetyzone.dto.CreateSafetyZoneRequest;
import com.oncare.oncare24.safetyzone.dto.SafetyZoneResponse;
import com.oncare.oncare24.safetyzone.dto.UpdateNotificationRequest;
import com.oncare.oncare24.safetyzone.dto.UpdateSafetyZoneRequest;
import com.oncare.oncare24.safetyzone.entity.SafetyZone;
import com.oncare.oncare24.safetyzone.repository.SafetyZoneRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oncare.oncare24.location.entity.ZoneVisitState;
import com.oncare.oncare24.location.repository.ZoneVisitStateRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * 안전구역 비즈니스 로직.
 * <p>
 * <b>권한 정책 3단계 (모든 변경 작업에 적용)</b>
 * <ol>
 *     <li>현재 사용자가 GUARDIAN 역할인지 (등록 시에만 검증; 수정/삭제는 isOwnedBy로 자연스럽게 차단됨)</li>
 *     <li>현재 사용자가 wardId 피보호자에 ACCEPTED 상태로 연결되어 있는지 (assertGuardianLinkedToWard)</li>
 *     <li>해당 zone의 guardianId가 현재 사용자와 일치하는지 (zone.isOwnedBy)</li>
 * </ol>
 *
 * <b>왜 3단계인가</b>
 * <ul>
 *     <li>(1)이 없으면 ELDER 계정이 본인 zone을 등록하는 시나리오가 의도치 않게 열림.</li>
 *     <li>(2)가 없으면 다른 가족의 피보호자에게 zone을 등록하는 보안 구멍이 열림.</li>
 *     <li>(3)이 없으면 같은 피보호자에 연결된 다른 보호자가 내가 만든 zone을 수정/삭제할 수 있음.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SafetyZoneService {

    private static final int MAX_ZONES_PER_WARD = 5;
    private final ZoneVisitStateRepository zoneVisitStateRepository;

    private final SafetyZoneRepository safetyZoneRepository;
    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;

    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public SafetyZoneResponse create(Long currentUserId, CreateSafetyZoneRequest req) {
        assertCurrentUserIsGuardian(currentUserId);
        assertWardIsElder(req.wardId());
        assertGuardianLinkedToWard(currentUserId, req.wardId());
        assertWardZoneLimitNotExceeded(req.wardId());

        SafetyZone zone = SafetyZone.builder()
                .wardId(req.wardId())
                .guardianId(currentUserId)
                .name(req.name())
                .type(req.type())
                .address(req.address())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .radius(req.radius())
                .build();

        SafetyZone saved = safetyZoneRepository.save(zone);
        return SafetyZoneResponse.from(saved);
    }

    // ============================================================
    // READ
    // ============================================================

    /**
     * 특정 피보호자의 안전구역 전체 조회.
     * <p>
     * 같은 피보호자에 연결된 다른 보호자가 만든 zone도 모두 조회 가능
     * (가족이 함께 모니터링하는 시나리오 — 시중 앱 표준).
     */
    @Transactional(readOnly = true)
    public List<SafetyZoneResponse> findAllByWard(Long currentUserId, Long wardId) {
        assertGuardianLinkedToWard(currentUserId, wardId);

        List<SafetyZone> zones = safetyZoneRepository
                .findByWardIdAndActiveTrueOrderByCreatedAtAsc(wardId);

        // ward의 모든 ZoneVisitState를 zoneId 키로 한 번에 로드 (N+1 방지)
        Map<Long, ZoneVisitState> visitMap = new HashMap<>();
        for (ZoneVisitState v : zoneVisitStateRepository.findByWardId(wardId)) {
            visitMap.put(v.getZoneId(), v);
        }

        return zones.stream()
                .map(zone -> SafetyZoneResponse.from(zone, visitMap.get(zone.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SafetyZoneResponse findById(Long currentUserId, Long zoneId) {
        SafetyZone zone = getActiveZoneOrThrow(zoneId);
        assertGuardianLinkedToWard(currentUserId, zone.getWardId());

        ZoneVisitState visit = zoneVisitStateRepository
                .findByWardIdAndZoneId(zone.getWardId(), zoneId)
                .orElse(null);

        return SafetyZoneResponse.from(zone, visit);
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Transactional
    public SafetyZoneResponse update(Long currentUserId, Long zoneId, UpdateSafetyZoneRequest req) {
        SafetyZone zone = getActiveZoneOrThrow(zoneId);
        assertGuardianLinkedToWard(currentUserId, zone.getWardId());
        assertOwnedByCurrentUser(zone, currentUserId);

        zone.update(
                req.name(),
                req.type(),
                req.address(),
                req.latitude(),
                req.longitude(),
                req.radius()
        );
        return SafetyZoneResponse.from(zone);
    }

    @Transactional
    public SafetyZoneResponse updateNotification(
            Long currentUserId,
            Long zoneId,
            UpdateNotificationRequest req
    ) {
        SafetyZone zone = getActiveZoneOrThrow(zoneId);
        assertGuardianLinkedToWard(currentUserId, zone.getWardId());
        assertOwnedByCurrentUser(zone, currentUserId);

        zone.changeNotificationEnabled(req.enabled());
        return SafetyZoneResponse.from(zone);
    }

    // ============================================================
    // DELETE (soft)
    // ============================================================

    @Transactional
    public void softDelete(Long currentUserId, Long zoneId) {
        SafetyZone zone = getActiveZoneOrThrow(zoneId);
        assertGuardianLinkedToWard(currentUserId, zone.getWardId());
        assertOwnedByCurrentUser(zone, currentUserId);

        zone.softDelete();
    }

    // ============================================================
    // 검증 헬퍼들
    // ============================================================

    private SafetyZone getActiveZoneOrThrow(Long zoneId) {
        return safetyZoneRepository.findByIdAndActiveTrue(zoneId)
                .orElseThrow(() -> new CustomException(ErrorCode.SAFETY_ZONE_NOT_FOUND));
    }

    private void assertCurrentUserIsGuardian(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.GUARDIAN) {
            throw new CustomException(ErrorCode.SAFETY_ZONE_ACCESS_DENIED);
        }
    }

    private void assertWardIsElder(Long wardId) {
        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ELDER));
        if (ward.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.INVALID_ELDER);
        }
    }

    private void assertGuardianLinkedToWard(Long guardianId, Long wardId) {
        boolean linked = guardianWardRepository
                .existsByGuardianIdAndWardIdAndStatus(
                        guardianId,
                        wardId,
                        GuardianWardStatus.ACCEPTED
                );
        if (!linked) {
            throw new CustomException(ErrorCode.NOT_LINKED_TO_WARD);
        }
    }

    private void assertWardZoneLimitNotExceeded(Long wardId) {
        long count = safetyZoneRepository.countByWardIdAndActiveTrue(wardId);
        if (count >= MAX_ZONES_PER_WARD) {
            throw new CustomException(ErrorCode.SAFETY_ZONE_LIMIT_EXCEEDED);
        }
    }

    private void assertOwnedByCurrentUser(SafetyZone zone, Long userId) {
        if (!zone.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.SAFETY_ZONE_ACCESS_DENIED);
        }
    }
}