package com.oncare.oncare24.guardian.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.dto.CreateInvitationRequest;
import com.oncare.oncare24.guardian.dto.SentInvitationResponse;
import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.guardian.util.InviteCodeGenerator;
import com.oncare.oncare24.notification.sender.SmsSender;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oncare.oncare24.guardian.dto.ReceivedInvitationResponse;
import java.time.LocalDateTime;
import java.util.List;
import com.oncare.oncare24.notification.service.NotificationService;
/**
 * 보호자-피보호자 초대 비즈니스 로직.
 * <p>
 * <b>create 흐름</b>
 * <ol>
 *     <li>현재 사용자가 GUARDIAN 역할인지</li>
 *     <li>입력한 phone에 ELDER 사용자가 존재하는지(없으면 G004로 일괄 응답 — 역할 노출 X)</li>
 *     <li>본인 초대 차단(G005)</li>
 *     <li>기존 매칭 분기:
 *         <ul>
 *             <li>ACCEPTED → G002 차단</li>
 *             <li>PENDING + 미만료 → G003 차단</li>
 *             <li>PENDING + 만료 → reInvite (새 코드·만료시각으로 갱신)</li>
 *             <li>REJECTED → reInvite</li>
 *             <li>없음 → 신규 row</li>
 *         </ul>
 *     </li>
 *     <li>SMS 발송 (현재는 LogOnly, Step 10에서 CoolSMS로 교체)</li>
 * </ol>
 *
 * <b>cancel 흐름</b>: PENDING 상태이고 본인이 발송한 초대만 hard delete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    /** 만료 기간 — Life360 표준 따라 72시간. */
    private static final long INVITATION_TTL_HOURS = 72;

    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final SmsSender smsSender;
    private final NotificationService notificationService;
    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public SentInvitationResponse create(Long currentUserId, CreateInvitationRequest req) {
        User guardian = assertCurrentUserIsGuardian(currentUserId);
        User ward = lookupWardByPhone(req.wardPhone());
        assertNotSelfInvite(currentUserId, ward.getId());

        GuardianWard invitation = guardianWardRepository
                .findByGuardianIdAndWardId(currentUserId, ward.getId())
                .map(existing -> reuseExistingMatch(existing, req.relationship()))
                .orElseGet(() -> createNewInvitation(currentUserId, ward.getId(), req.relationship()));

        sendInvitationSms(ward.getPhone(), guardian.getName());
        notificationService.notifyWardInvitation(ward.getId(), guardian.getName());

        log.info("[Invitation-Create] guardianId={}, wardId={}, status={}, code={}",
                currentUserId, ward.getId(), invitation.getStatus(), invitation.getInviteCode());

        return SentInvitationResponse.from(invitation, ward);
    }

    // ============================================================
    // READ — 보낸 초대 목록 (PENDING만)
    // ============================================================

    @Transactional(readOnly = true)
    public List<SentInvitationResponse> findAllSent(Long currentUserId) {
        assertCurrentUserIsGuardian(currentUserId);

        List<GuardianWard> invitations = guardianWardRepository
                .findByGuardianIdAndStatusOrderByCreatedAtDesc(currentUserId, GuardianWardStatus.PENDING);

        // ward 정보 N+1 방지: ward들 한 번에 조회 후 매핑
        List<Long> wardIds = invitations.stream().map(GuardianWard::getWardId).toList();
        var wardMap = userRepository.findAllById(wardIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        return invitations.stream()
                .map(gw -> SentInvitationResponse.from(gw, wardMap.get(gw.getWardId())))
                .toList();
    }

    // ============================================================
    // DELETE — 보낸 초대 취소 (hard delete)
    // ============================================================

    @Transactional
    public void cancel(Long currentUserId, Long invitationId) {
        GuardianWard invitation = guardianWardRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_NOT_FOUND));

        if (!invitation.getGuardianId().equals(currentUserId)) {
            throw new CustomException(ErrorCode.INVITATION_ACCESS_DENIED);
        }
        if (!invitation.isPending()) {
            throw new CustomException(ErrorCode.INVITATION_ALREADY_RESPONDED);
        }

        guardianWardRepository.delete(invitation);
        log.info("[Invitation-Cancel] invitationId={}, guardianId={}", invitationId, currentUserId);
    }

    // ============================================================
    // ELDER — 받은 초대 목록
    // ============================================================

    @Transactional(readOnly = true)
    public List<ReceivedInvitationResponse> findAllReceived(Long currentUserId) {
        assertCurrentUserIsElder(currentUserId);

        List<GuardianWard> invitations = guardianWardRepository
                .findByWardIdAndStatusOrderByCreatedAtDesc(currentUserId, GuardianWardStatus.PENDING);

        // guardian 정보 N+1 방지 (Sent 패턴과 동일)
        List<Long> guardianIds = invitations.stream().map(GuardianWard::getGuardianId).toList();
        var guardianMap = userRepository.findAllById(guardianIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        return invitations.stream()
                // 만료된 PENDING은 받은 목록에서 자동으로 숨김 (피보호자 입장 UX)
                .filter(gw -> !gw.isExpired())
                .map(gw -> ReceivedInvitationResponse.from(gw, guardianMap.get(gw.getGuardianId())))
                .toList();
    }

    // ============================================================
    // ELDER — 수락
    // ============================================================

    @Transactional
    public ReceivedInvitationResponse accept(Long currentUserId, Long invitationId) {
        assertCurrentUserIsElder(currentUserId);
        GuardianWard invitation = getInvitationOrThrow(invitationId);
        assertInvitationAddressedTo(invitation, currentUserId);
        assertInvitationStillPending(invitation);

        invitation.accept();

        User guardian = userRepository.findById(invitation.getGuardianId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        log.info("[Invitation-Accept] invitationId={}, wardId={}, guardianId={}",
                invitationId, currentUserId, guardian.getId());

        return ReceivedInvitationResponse.from(invitation, guardian);
    }

    // ============================================================
    // ELDER — 거절
    // ============================================================

    @Transactional
    public void reject(Long currentUserId, Long invitationId) {
        assertCurrentUserIsElder(currentUserId);
        GuardianWard invitation = getInvitationOrThrow(invitationId);
        assertInvitationAddressedTo(invitation, currentUserId);
        assertInvitationStillPending(invitation);

        invitation.reject();
        log.info("[Invitation-Reject] invitationId={}, wardId={}", invitationId, currentUserId);
    }

    // ============================================================
    // 추가 검증 헬퍼
    // ============================================================

    private User assertCurrentUserIsElder(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.ROLE_NOT_ELDER);
        }
        return user;
    }

    private GuardianWard getInvitationOrThrow(Long invitationId) {
        return guardianWardRepository.findById(invitationId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_NOT_FOUND));
    }

    private void assertInvitationAddressedTo(GuardianWard invitation, Long currentUserId) {
        if (!invitation.getWardId().equals(currentUserId)) {
            throw new CustomException(ErrorCode.INVITATION_ACCESS_DENIED);
        }
    }

    private void assertInvitationStillPending(GuardianWard invitation) {
        if (!invitation.isPending()) {
            throw new CustomException(ErrorCode.INVITATION_ALREADY_RESPONDED);
        }
        if (invitation.isExpired()) {
            throw new CustomException(ErrorCode.INVITATION_EXPIRED);
        }
    }

    // ============================================================
    // 내부 헬퍼들
    // ============================================================

    private GuardianWard reuseExistingMatch(GuardianWard existing, String relationship) {
        switch (existing.getStatus()) {
            case ACCEPTED -> throw new CustomException(ErrorCode.DUPLICATE_WARD_LINK);
            case PENDING -> {
                if (!existing.isExpired()) {
                    throw new CustomException(ErrorCode.PENDING_INVITATION_EXISTS);
                }
                // PENDING이지만 만료됨 → 재발급
                existing.reInvite(
                        inviteCodeGenerator.generateUnique(),
                        relationship,
                        LocalDateTime.now().plusHours(INVITATION_TTL_HOURS)
                );
                return existing;
            }
            case REJECTED -> {
                existing.reInvite(
                        inviteCodeGenerator.generateUnique(),
                        relationship,
                        LocalDateTime.now().plusHours(INVITATION_TTL_HOURS)
                );
                return existing;
            }
            default -> throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private GuardianWard createNewInvitation(Long guardianId, Long wardId, String relationship) {
        GuardianWard invitation = GuardianWard.builder()
                .guardianId(guardianId)
                .wardId(wardId)
                .inviteCode(inviteCodeGenerator.generateUnique())
                .relationship(relationship)
                .expiresAt(LocalDateTime.now().plusHours(INVITATION_TTL_HOURS))
                .build();
        return guardianWardRepository.save(invitation);
    }

    private void sendInvitationSms(String wardPhone, String guardianName) {
        String body = String.format(
                "[보살핌] %s님이 보호자가 되고 싶어해요. 보살핌을 열어 보세요.",
                guardianName
        );
        smsSender.send(wardPhone, body);
    }

    // === 검증 헬퍼 ===

    private User assertCurrentUserIsGuardian(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.GUARDIAN) {
            throw new CustomException(ErrorCode.ROLE_NOT_GUARDIAN);
        }
        return user;
    }

    private User lookupWardByPhone(String wardPhone) {
        // 노출 정책: 번호가 없거나 GUARDIAN으로 가입된 경우 모두 G004로 통일 (역할 leak 방지)
        return userRepository.findByPhone(wardPhone)
                .filter(u -> u.getRole() == UserRole.ELDER)
                .orElseThrow(() -> new CustomException(ErrorCode.WARD_NOT_FOUND_BY_PHONE));
    }

    private void assertNotSelfInvite(Long currentUserId, Long wardId) {
        if (currentUserId.equals(wardId)) {
            throw new CustomException(ErrorCode.SELF_INVITE_NOT_ALLOWED);
        }
    }
}