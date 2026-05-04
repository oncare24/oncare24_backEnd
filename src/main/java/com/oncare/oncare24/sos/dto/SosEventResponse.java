package com.oncare.oncare24.sos.dto;

import com.oncare.oncare24.sos.entity.SosEvent;
import com.oncare.oncare24.sos.entity.SosLocationSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SOS 호출 결과 응답.
 * <p>
 * 프론트는 이 응답으로 결과 화면을 구성:
 * <ul>
 *     <li>notifiedGuardianCount &gt; 0: "보호자 N명에게 알림이 갔어요"</li>
 *     <li>notifiedGuardianCount == 0: "연결된 보호자가 없어요. 119에 직접 전화해주세요"</li>
 * </ul>
 */
@Schema(description = "SOS 호출 결과")
public record SosEventResponse(
        Long eventId,
        BigDecimal latitude,
        BigDecimal longitude,
        SosLocationSource locationSource,
        int notifiedGuardianCount,
        LocalDateTime createdAt
) {
    public static SosEventResponse from(SosEvent e) {
        return new SosEventResponse(
                e.getId(),
                e.getLatitude(),
                e.getLongitude(),
                e.getLocationSource(),
                e.getNotifiedGuardianCount(),
                e.getCreatedAt()
        );
    }
}