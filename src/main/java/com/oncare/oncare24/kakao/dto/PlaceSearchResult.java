package com.oncare.oncare24.kakao.dto;

/**
 * 프론트로 내려줄 장소 검색 단건 결과.
 * <p>
 * 카카오 응답에서 프론트에 필요한 필드만 추려 노출. 카테고리 등 메타정보는 단순화.
 */
public record PlaceSearchResult(
        String placeName,       // "양산역"
        String addressName,     // "경상남도 양산시 물금읍 양산역4길 1"
        String roadAddressName, // 도로명 주소 (없으면 null)
        double latitude,
        double longitude,
        String category         // "교통,수송 > 기차,철도 > 전철역" — 화면에 부가정보로 표시
) {
}