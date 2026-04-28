package com.oncare.oncare24.guardian.entity;

import com.oncare.oncare24.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 보호자-피보호자 연동 엔티티 (N:M).
 * <p>
 * <b>설계 포인트</b>
 * <ul>
 *     <li>한 피보호자(ward)에 여러 보호자(guardian) 가능, 한 보호자가 여러 피보호자 모니터링 가능 — N:M.</li>
 *     <li>복합 UNIQUE (ward_id, guardian_id): 같은 짝이 여러 번 들어가는 것을 DB에서 차단.
 *         REJECTED → PENDING 재초대 시에는 row를 재사용(reInvite).</li>
 *     <li>invite_code: 6자리 숫자 토큰. UNIQUE. 향후 deep-link 진입 케이스 대비해 유지하지만,
 *         Step 9 SMS에는 노출하지 않음 (받은 목록 카드 탭 수락 패턴).</li>
 *     <li>expires_at: PENDING 상태일 때만 의미 있음. ACCEPTED 후엔 검증 대상 아님.
 *         Life360 표준 따라 72시간(3일).</li>
 *     <li>relationship: 보호자가 입력하는 메모성 라벨("어머니", "아버지" 등). nullable.</li>
 * </ul>
 */
@Entity
@Getter
@Table(
        name = "guardian_ward",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_guardian_ward_pair",
                        columnNames = {"ward_id", "guardian_id"}
                ),
                @UniqueConstraint(
                        name = "uk_guardian_ward_invite_code",
                        columnNames = "invite_code"
                )
        },
        indexes = {
                @Index(name = "idx_guardian_ward_guardian", columnList = "guardian_id, status"),
                @Index(name = "idx_guardian_ward_ward", columnList = "ward_id, status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianWard extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 피보호자(ELDER) user_id */
    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    /** 보호자(GUARDIAN) user_id */
    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GuardianWardStatus status;

    /** 6자리 숫자 토큰. unique. SMS에는 노출하지 않지만 미래 deep-link 호환 위해 유지. */
    @Column(name = "invite_code", length = 50)
    private String inviteCode;

    /** 보호자가 입력하는 관계 메모. "어머니", "아버지" 등. nullable. */
    @Column(name = "relationship", length = 20)
    private String relationship;

    /** PENDING 만료 시각. ACCEPTED·REJECTED에서는 무의미. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Builder
    private GuardianWard(Long wardId, Long guardianId, String inviteCode,
                         String relationship, LocalDateTime expiresAt) {
        this.wardId = wardId;
        this.guardianId = guardianId;
        this.status = GuardianWardStatus.PENDING;
        this.inviteCode = inviteCode;
        this.relationship = relationship;
        this.expiresAt = expiresAt;
    }

    // === 비즈니스 메서드 ===

    public void accept() {
        this.status = GuardianWardStatus.ACCEPTED;
        // 수락 후엔 expiresAt 무의미. 굳이 비우진 않음 (audit용).
    }

    public void reject() {
        this.status = GuardianWardStatus.REJECTED;
    }

    /** 거절·만료된 매칭 재초대 시 사용. invite_code·relationship·expiresAt 모두 갱신. */
    public void reInvite(String newInviteCode, String newRelationship,
                         LocalDateTime newExpiresAt) {
        this.status = GuardianWardStatus.PENDING;
        this.inviteCode = newInviteCode;
        this.relationship = newRelationship;
        this.expiresAt = newExpiresAt;
    }

    public boolean isAccepted() {
        return this.status == GuardianWardStatus.ACCEPTED;
    }

    public boolean isPending() {
        return this.status == GuardianWardStatus.PENDING;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}