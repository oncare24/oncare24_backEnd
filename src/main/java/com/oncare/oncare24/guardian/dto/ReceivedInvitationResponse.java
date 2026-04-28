package com.oncare.oncare24.guardian.dto;

import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.user.entity.User;

import java.time.LocalDateTime;

/**
 * 피보호자 시점 — 내가 받은 초대 응답.
 * <p>
 * <b>Sent 응답과의 차이</b>
 * <ul>
 *     <li>inviteCode 노출 안 함 — 받은 목록 카드 탭으로 수락하므로 코드 불필요(보안 최소노출).</li>
 *     <li>guardianName/relationship만 표시 — "OO님이 OO으로 연동을 요청했어요" UI에 충분.</li>
 *     <li>guardianPhoneMasked: 어르신이 보호자 신원 확인용으로 끝 4자리만 보여줌(피싱 초대 방어).</li>
 * </ul>
 */
public record ReceivedInvitationResponse(
        Long id,
        Long guardianId,
        String guardianName,
        String guardianPhoneMasked,
        String relationship,
        GuardianWardStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
    public static ReceivedInvitationResponse from(GuardianWard gw, User guardian) {
        return new ReceivedInvitationResponse(
                gw.getId(),
                guardian.getId(),
                guardian.getName(),
                maskPhone(guardian.getPhone()),
                gw.getRelationship(),
                gw.getStatus(),
                gw.getExpiresAt(),
                gw.getCreatedAt()
        );
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return "***";
        return phone.substring(0, 3) + "-****-" + phone.substring(phone.length() - 4);
    }
}