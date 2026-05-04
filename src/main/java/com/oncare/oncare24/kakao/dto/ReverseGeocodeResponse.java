package com.oncare.oncare24.kakao.dto;

/**
 * 좌표 → 주소 변환 응답.
 * <p>
 * 카카오 coord2address API 응답을 프론트 친화 포맷으로 단순화.
 * <p>
 * <b>둘 다 null인 경우</b>: 바다 위·산간 등 카카오에 등록된 행정구역이 없는 좌표.
 * 프론트는 두 필드 다 비어있으면 안내 문구를 띄우거나 사용자가 직접 입력하도록 폴백.
 */
public record ReverseGeocodeResponse(
        String roadAddress,  // 도로명 주소 (있으면 우선 사용)
        String address       // 지번 주소 (도로명 없으면 fallback)
) {
}