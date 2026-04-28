package com.oncare.oncare24.safetyzone.dto;

import com.oncare.oncare24.safetyzone.entity.SafetyZoneType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * 안전구역 수정 요청.
 * <p>
 * 수정 화면에서 모든 필드를 통째로 보내옴(부분 수정 X) — PUT 시멘틱.
 * wardId 변경 불가(이동시키려면 삭제 후 재등록).
 */
public record UpdateSafetyZoneRequest(

        @NotBlank(message = "안전구역 이름을 입력해주세요")
        @Size(max = 50, message = "이름은 50자 이내로 입력해주세요")
        String name,

        @NotNull(message = "장소 종류를 선택해주세요")
        SafetyZoneType type,

        @NotBlank(message = "주소를 입력해주세요")
        @Size(max = 200, message = "주소는 200자 이내로 입력해주세요")
        String address,

        @NotNull(message = "위도가 필요해요")
        @DecimalMin(value = "-90.0", message = "위도 범위가 올바르지 않아요")
        @DecimalMax(value = "90.0", message = "위도 범위가 올바르지 않아요")
        BigDecimal latitude,

        @NotNull(message = "경도가 필요해요")
        @DecimalMin(value = "-180.0", message = "경도 범위가 올바르지 않아요")
        @DecimalMax(value = "180.0", message = "경도 범위가 올바르지 않아요")
        BigDecimal longitude,

        @NotNull(message = "반경이 필요해요")
        @Min(value = 200, message = "반경은 최소 200m 이상이어야 해요")
        @Max(value = 1000, message = "반경은 최대 1000m까지 가능해요")
        Integer radius
) {
}