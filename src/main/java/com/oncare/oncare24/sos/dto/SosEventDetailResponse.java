package com.oncare.oncare24.sos.dto;

import com.oncare.oncare24.sos.entity.SosEvent;
import com.oncare.oncare24.sos.entity.SosLocationSource;
import com.oncare.oncare24.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SOS 이벤트 단건 상세 응답.
 * <p>
 * 보호자가 알림 탭 → SosLocationView 진입 시 사용. trigger 응답(SosEventResponse)과 달리
 * 피보호자 이름·전화번호도 포함하여 SosLocationView가 즉시 전화 걸 수 있게 함.
 */
@Schema(description = "SOS 이벤트 상세 (보호자 SosLocationView용)")
public record SosEventDetailResponse(
        Long eventId,
        Long wardId,
        String wardName,
        String wardPhone,
        BigDecimal latitude,
        BigDecimal longitude,
        SosLocationSource locationSource,
        int notifiedGuardianCount,
        LocalDateTime createdAt
) {
    public static SosEventDetailResponse of(SosEvent e, User ward) {
        return new SosEventDetailResponse(
                e.getId(),
                e.getWardId(),
                ward.getName(),
                ward.getPhone(),
                e.getLatitude(),
                e.getLongitude(),
                e.getLocationSource(),
                e.getNotifiedGuardianCount(),
                e.getCreatedAt()
        );
    }
}