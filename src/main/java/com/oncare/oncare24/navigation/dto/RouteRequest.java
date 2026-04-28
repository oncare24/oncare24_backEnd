package com.oncare.oncare24.navigation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 길안내 요청 (도보/대중교통 공통).
 * <p>
 * 출발지: 보통 사용자 현재 GPS 위치 (프론트에서 전달).
 * 도착지: 병원 추천 응답에서 사용자가 선택한 병원의 좌표.
 *
 * @param startLat   출발 위도 (한국 영토 33.0~39.0)
 * @param startLon   출발 경도 (한국 영토 124.0~132.0)
 * @param endLat     도착 위도
 * @param endLon     도착 경도
 * @param endName    도착지 이름 (선택). 카드의 "도착" 단계에 "OO병원 도착"으로 표시.
 */
public record RouteRequest(

        @NotNull(message = "출발 위도는 필수입니다.")
        @DecimalMin(value = "33.0", message = "위도가 한국 영토 범위를 벗어납니다.")
        @DecimalMax(value = "39.0", message = "위도가 한국 영토 범위를 벗어납니다.")
        Double startLat,

        @NotNull(message = "출발 경도는 필수입니다.")
        @DecimalMin(value = "124.0", message = "경도가 한국 영토 범위를 벗어납니다.")
        @DecimalMax(value = "132.0", message = "경도가 한국 영토 범위를 벗어납니다.")
        Double startLon,

        @NotNull(message = "도착 위도는 필수입니다.")
        @DecimalMin(value = "33.0")
        @DecimalMax(value = "39.0")
        Double endLat,

        @NotNull(message = "도착 경도는 필수입니다.")
        @DecimalMin(value = "124.0")
        @DecimalMax(value = "132.0")
        Double endLon,

        @Size(max = 100, message = "도착지 이름은 100자 이내여야 합니다.")
        String endName
) {
    public String endNameOrDefault() {
        return endName == null || endName.isBlank() ? "도착지" : endName;
    }
}
