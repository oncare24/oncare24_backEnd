package com.oncare.oncare24.guardian.repository;

import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * GuardianWard Repository.
 * <p>
 * 권한 검증(existsBy...)·매칭 조회·초대 목록 조회를 모두 담당.
 */
public interface GuardianWardRepository extends JpaRepository<GuardianWard, Long> {

    /** 권한 검증의 1차 게이트 — ACCEPTED 매칭이 있는지. */
    boolean existsByGuardianIdAndWardIdAndStatus(
            Long guardianId,
            Long wardId,
            GuardianWardStatus status
    );

    /** 이탈감지 배치(Step 8)에서 ward의 모든 보호자 조회. */
    List<GuardianWard> findByWardIdAndStatus(Long wardId, GuardianWardStatus status);

    /** 초대 코드로 lookup (충돌 검증·deep-link 호환). */
    Optional<GuardianWard> findByInviteCode(String inviteCode);

    /** 동일 (guardian, ward) 매칭이 이미 있는지 — 중복 초대/재초대 분기용. */
    Optional<GuardianWard> findByGuardianIdAndWardId(Long guardianId, Long wardId);

    /** 보호자 시점: 내가 보낸 초대 목록(상태별, 최신순). */
    List<GuardianWard> findByGuardianIdAndStatusOrderByCreatedAtDesc(
            Long guardianId,
            GuardianWardStatus status
    );

    /** 피보호자 시점: 받은 초대 목록(상태별, 최신순) — 9-B에서 사용. */
    List<GuardianWard> findByWardIdAndStatusOrderByCreatedAtDesc(
            Long wardId,
            GuardianWardStatus status
    );
}