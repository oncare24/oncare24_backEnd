package com.oncare.oncare24.guardian.dto;

import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 보호자 홈 카드 한 장에 필요한 모든 정보.
 * <p>
 * <b>마스킹 정책</b>: 가족 관계라 어차피 보호자가 ward의 폰번호를 알지만,
 * 응답에 평문 노출은 PII 최소노출 원칙 위배. 끝 4자리만 표시.
 */
public record WardResponse(
        @Schema(description = "피보호자 ID", example = "2")
        Long wardId,
        @Schema(description = "피보호자 이름", example = "박피보호")
        String name,
        @Schema(description = "마스킹된 피보호자 전화번호", example = "010-****-5678")
        String phoneMasked,
        @Schema(description = "보호자와 피보호자의 관계", example = "딸")
        String relationship,
        @Schema(description = "피보호자 현재 상태", example = "INSIDE")
        WardStatus status,
        @Schema(description = "마지막 위치 표시 문구", example = "서울시 강남구")
        String locationLabel,
        @Schema(description = "마지막 위치 보고 후 경과 시간(분)", example = "12")
        Long lastReportedMinutesAgo,
        @Schema(description = "연동 완료 일시", example = "2026-05-13T10:00:00")
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
