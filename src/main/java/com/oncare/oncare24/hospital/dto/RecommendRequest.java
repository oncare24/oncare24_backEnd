package com.oncare.oncare24.hospital.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 병원 추천 요청.
 * <p>
 * - {@code symptoms}: 사용자가 입력한 증상 자연어. 음성 → 텍스트 변환된 결과도 그대로 입력 가능.
 * - {@code latitude}, {@code longitude}: 현재 GPS 위치 (선택). 없으면 서버가 폴백 체인으로 결정:
 *   <ol>
 *       <li>요청 본문의 lat/lon</li>
 *       <li>최근 5분 내 LocationReport (보고된 마지막 위치)</li>
 *       <li>안전구역 첫 번째 (보통 "집") 중심점</li>
 *       <li>모두 없으면 400</li>
 *   </ol>
 * - {@code radius}: 검색 반경(미터). 기본 5000m (5km). 1000~20000 사이.
 */
public record RecommendRequest(

        @NotBlank(message = "증상은 필수입니다.")
        @Size(min = 2, max = 500, message = "증상은 2자 이상 500자 이하로 입력해주세요.")
        String symptoms,

        @DecimalMin(value = "33.0", message = "위도가 한국 영토 범위를 벗어납니다.")
        @DecimalMax(value = "39.0", message = "위도가 한국 영토 범위를 벗어납니다.")
        Double latitude,

        @DecimalMin(value = "124.0", message = "경도가 한국 영토 범위를 벗어납니다.")
        @DecimalMax(value = "132.0", message = "경도가 한국 영토 범위를 벗어납니다.")
        Double longitude,

        Integer radius
) {
    public static final int DEFAULT_RADIUS_METERS = 5000;
    public static final int MIN_RADIUS_METERS = 1000;
    public static final int MAX_RADIUS_METERS = 20000;

    public int radiusOrDefault() {
        if (radius == null) return DEFAULT_RADIUS_METERS;
        return Math.max(MIN_RADIUS_METERS, Math.min(MAX_RADIUS_METERS, radius));
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
