package com.oncare.oncare24.guardian.dto;

import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.user.entity.User;

import java.time.LocalDateTime;

/**
 * 보호자 홈 카드 한 장에 필요한 모든 정보.
 * <p>
 * <b>마스킹 정책</b>: 가족 관계라 어차피 보호자가 ward의 폰번호를 알지만,
 * 응답에 평문 노출은 PII 최소노출 원칙 위배. 끝 4자리만 표시.
 */
public record WardResponse(
        Long wardId,
        String name,
        String phoneMasked,
        String relationship,
        WardStatus status,
        String locationLabel,
        Long lastReportedMinutesAgo,
        LocalDateTime linkedAt
) {
    /**
     * 모든 필드를 호출자가 채워서 생성. 상태 enrichment는 Service 레이어에서.
     */
    public static WardResponse of(
            GuardianWard link,
            User ward,
            WardStatus status,
            String locationLabel,
            Long lastReportedMinutesAgo
    ) {
        return new WardResponse(
                ward.getId(),
                ward.getName(),
                maskPhone(ward.getPhone()),
                link.getRelationship(),
                status,
                locationLabel,
                lastReportedMinutesAgo,
                link.getUpdatedAt()  // ACCEPTED 시점 (BaseTimeEntity가 자동 갱신)
        );
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return "***";
        return phone.substring(0, 3) + "-****-" + phone.substring(phone.length() - 4);
    }
}