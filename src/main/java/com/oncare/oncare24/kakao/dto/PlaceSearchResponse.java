package com.oncare.oncare24.kakao.dto;

import java.util.List;

/**
 * 키워드 검색 응답. results + 페이징 정보.
 */
public record PlaceSearchResponse(
        List<PlaceSearchResult> results,
        boolean hasMore,    // 다음 페이지 존재 여부 (카카오 is_end의 반대)
        int totalCount      // 전체 검색 결과 수 (카카오 pageable_count, 최대 45)
) {
}