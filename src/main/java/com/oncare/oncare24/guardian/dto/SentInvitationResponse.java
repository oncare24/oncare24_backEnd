package com.oncare.oncare24.guardian.dto;

import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 보호자 시점 — 내가 보낸 초대 응답.
 * <p>
 * <b>마스킹 정책</b>: ward의 전화번호는 끝 4자리만 노출(010-****-1234).
 * 보호자가 직접 입력한 번호지만 응답 페이로드·로그에 평문이 머무는 걸 막음 (PII 최소노출 원칙).
 * <p>
 * <b>inviteCode 노출</b>: SMS에는 안 넣지만, 보호자 화면에는 표시(분실 폰 케이스에서 보호자가
 * 어르신에게 직접 코드를 알려주는 폴백). 받는 사람이 본인이라 노출 OK.
 */
public record SentInvitationResponse(
        @Schema(description = "초대 ID", example = "10")
        Long id,
        @Schema(description = "초대 코드", example = "A1B2C3")
        String inviteCode,
        @Schema(description = "피보호자 ID", example = "2")
        Long wardId,
        @Schema(description = "피보호자 이름", example = "박피보호")
        String wardName,
        @Schema(description = "마스킹된 피보호자 전화번호", example = "010-****-5678")
        String wardPhoneMasked,
        @Schema(description = "보호자와 피보호자의 관계", example = "딸")
        String relationship,
        @Schema(description = "초대 상태", example = "PENDING")
        GuardianWardStatus status,
        @Schema(description = "초대 만료 일시", example = "2026-05-14T10:00:00")
        LocalDateTime expiresAt,
        @Schema(description = "초대 생성 일시", example = "2026-05-13T10:00:00")
        LocalDateTime createdAt
) {
    public static SentInvitationResponse from(GuardianWard gw, User ward) {
        return new SentInvitationResponse(
                gw.getId(),
                gw.getInviteCode(),
                ward.getId(),
                ward.getName(),
                maskPhone(ward.getPhone()),
                gw.getRelationship(),
                gw.getStatus(),
                gw.getExpiresAt(),
                gw.getCreatedAt()
        );
    }

    /** 01012345678 → 010-****-5678 */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return "***";
        return phone.substring(0, 3) + "-****-" + phone.substring(phone.length() - 4);
    }
}
