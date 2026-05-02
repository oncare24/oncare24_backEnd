package com.oncare.oncare24.kakao.client;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.kakao.dto.PlaceSearchResponse;
import com.oncare.oncare24.kakao.dto.PlaceSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 카카오 로컬 API 호출 클라이언트.
 * <p>
 * 카카오 응답 → 프론트 친화 DTO 매핑까지 책임. 비즈니스 로직 X.
 */
@Slf4j
@Component
public class KakaoLocalClient {

    private final RestClient kakaoRestClient;

    public KakaoLocalClient(@Qualifier("kakaoRestClient") RestClient kakaoRestClient) {
        this.kakaoRestClient = kakaoRestClient;
    }

    /**
     * 키워드 검색. 장소명/주소 모두 검색 가능 ("양산역", "삼성병원", "양산시 중앙로").
     *
     * @param query 검색어 (1~80자)
     * @param page  1-indexed 페이지 번호 (1~45)
     * @param size  페이지당 결과 수 (1~15)
     */
    @SuppressWarnings("unchecked")
    public PlaceSearchResponse searchByKeyword(String query, int page, int size) {
        try {
            Map<String, Object> raw = kakaoRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (raw == null) return new PlaceSearchResponse(List.of(), false, 0);

            List<Map<String, Object>> documents =
                    (List<Map<String, Object>>) raw.getOrDefault("documents", Collections.emptyList());
            Map<String, Object> meta =
                    (Map<String, Object>) raw.getOrDefault("meta", Collections.emptyMap());

            List<PlaceSearchResult> results = documents.stream()
                    .map(this::toResult)
                    .toList();

            boolean isEnd = Boolean.TRUE.equals(meta.get("is_end"));
            int totalCount = ((Number) meta.getOrDefault("pageable_count", 0)).intValue();

            return new PlaceSearchResponse(results, !isEnd, totalCount);

        } catch (RestClientException e) {
            log.error("[KAKAO-LOCAL] keyword search failed. query={}", query, e);
            throw new CustomException(ErrorCode.KAKAO_SEARCH_FAILED);
        }
    }

    /** 카카오 단건 응답 → 내부 DTO. x=longitude, y=latitude (카카오 명세). */
    private PlaceSearchResult toResult(Map<String, Object> doc) {
        String roadAddress = (String) doc.get("road_address_name");
        return new PlaceSearchResult(
                (String) doc.get("place_name"),
                (String) doc.get("address_name"),
                (roadAddress != null && !roadAddress.isBlank()) ? roadAddress : null,
                Double.parseDouble((String) doc.get("y")),
                Double.parseDouble((String) doc.get("x")),
                (String) doc.get("category_name")
        );
    }
}