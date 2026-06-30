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
import com.oncare.oncare24.kakao.dto.ReverseGeocodeResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * 카카오 로컬 API 호출 클라이언트.
 * <p>
 * 카카오 응답 → 프론트 친화 DTO 매핑까지 책임. 비즈니스 로직 X.
 * <p>
 * <b>두 가지 검색 API를 동시 호출하는 이유</b>:
 * 카카오 keyword API는 POI(이름이 등록된 장소)만 검색되고 일반 도로명/지번 주소는 빈 결과를 반환함.
 * 카카오맵 앱 자체는 keyword + address 두 개를 합쳐서 보여주므로 동일한 UX를 위해 백엔드에서 합쳐 내려줌.
 */
@Slf4j
@Component
public class KakaoLocalClient {

    private final RestClient kakaoRestClient;

    public KakaoLocalClient(@Qualifier("kakaoRestClient") RestClient kakaoRestClient) {
        this.kakaoRestClient = kakaoRestClient;
    }

    /**
     * 통합 검색. address API + keyword API 동시 호출 후 결과를 합쳐서 반환.
     * <p>
     * 정렬 우선순위: address 결과 먼저, keyword 결과 뒤. 같은 좌표 중복은 제거(좌표 6자리 반올림 비교).
     * <p>
     * 페이징: 두 API 결과를 합쳐 size만큼 자름. totalCount는 두 API 합산. hasMore는 둘 중 하나라도 더 있으면 true.
     */
    public PlaceSearchResponse search(String query, int page, int size) {
        PlaceSearchResponse addressResult = searchByAddress(query, page, size);
        PlaceSearchResponse keywordResult = searchByKeyword(query, page, size);

        // 좌표 기반 중복 제거 (소수점 6자리 ≈ 11cm 정밀도, 같은 건물로 충분)
        Set<String> seen = new HashSet<>();
        List<PlaceSearchResult> merged = new ArrayList<>();

        for (PlaceSearchResult r : addressResult.results()) {
            String key = coordKey(r);
            if (seen.add(key)) merged.add(r);
        }
        for (PlaceSearchResult r : keywordResult.results()) {
            String key = coordKey(r);
            if (seen.add(key)) merged.add(r);
        }

        // size 초과분은 자름
        List<PlaceSearchResult> capped = merged.size() > size ? merged.subList(0, size) : merged;

        return new PlaceSearchResponse(
                capped,
                addressResult.hasMore() || keywordResult.hasMore(),
                addressResult.totalCount() + keywordResult.totalCount()
        );
    }

    /**
     * 키워드(POI) 검색. 장소명/랜드마크용 ("양산역", "삼성병원").
     * 일반 주소는 빈 결과가 정상 — 그건 address 검색 사용.
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
                    .map(this::toKeywordResult)
                    .toList();

            boolean isEnd = Boolean.TRUE.equals(meta.get("is_end"));
            int totalCount = ((Number) meta.getOrDefault("pageable_count", 0)).intValue();

            return new PlaceSearchResponse(results, !isEnd, totalCount);

        } catch (RestClientException e) {
            log.error("[KAKAO-LOCAL] keyword search failed. query={}", query, e);
            throw new CustomException(ErrorCode.KAKAO_SEARCH_FAILED);
        }
    }

    /**
     * 주소(도로명/지번) 검색. "경상남도 양산시 중앙로 39", "양산시 중앙로 39" 모두 가능.
     * keyword와 응답 스키마가 다름 — documents[].address / road_address 객체 안에 좌표·이름이 들어있음.
     */
    @SuppressWarnings("unchecked")
    public PlaceSearchResponse searchByAddress(String query, int page, int size) {
        try {
            Map<String, Object> raw = kakaoRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/address.json")
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
                    .map(this::toAddressResult)
                    .toList();

            boolean isEnd = Boolean.TRUE.equals(meta.get("is_end"));
            int totalCount = ((Number) meta.getOrDefault("pageable_count", 0)).intValue();

            return new PlaceSearchResponse(results, !isEnd, totalCount);

        } catch (RestClientException e) {
            log.error("[KAKAO-LOCAL] address search failed. query={}", query, e);
            throw new CustomException(ErrorCode.KAKAO_SEARCH_FAILED);
        }
    }
    /**
     * 좌표 → 주소 변환 (Reverse Geocoding).
     * <p>
     * 안전구역 등록/수정 화면에서 지도 핀이 멈출 때마다 호출되어
     * 핀 위치의 주소를 자동으로 입력란에 채워넣는 용도.
     * <p>
     * 카카오 응답: documents[0].road_address.address_name (도로명) +
     *             documents[0].address.address_name (지번).
     * 도로명이 우선, 없으면 지번. 둘 다 없으면 빈 응답 (해상/산간).
     */
    @SuppressWarnings("unchecked")
    public ReverseGeocodeResponse coord2address(
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude
    ) {
        try {
            Map<String, Object> raw = kakaoRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/geo/coord2address.json")
                            // 카카오 명세: x=경도, y=위도 (헷갈리지 않게 주의)
                            .queryParam("x", longitude.toPlainString())
                            .queryParam("y", latitude.toPlainString())
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (raw == null) {
                return new ReverseGeocodeResponse(null, null);
            }

            List<Map<String, Object>> documents = (List<Map<String, Object>>)
                    raw.getOrDefault("documents", Collections.emptyList());

            if (documents.isEmpty()) {
                return new ReverseGeocodeResponse(null, null);
            }

            Map<String, Object> doc = documents.get(0);
            Map<String, Object> roadAddressMap = (Map<String, Object>) doc.get("road_address");
            Map<String, Object> addressMap = (Map<String, Object>) doc.get("address");

            String roadAddress = (roadAddressMap != null)
                    ? (String) roadAddressMap.get("address_name")
                    : null;
            String address = (addressMap != null)
                    ? (String) addressMap.get("address_name")
                    : null;

            // 빈 문자열은 null로 정규화 (프론트에서 ?? 폴백 단순화)
            return new ReverseGeocodeResponse(
                    (roadAddress != null && !roadAddress.isBlank()) ? roadAddress : null,
                    (address != null && !address.isBlank()) ? address : null
            );

        } catch (RestClientException e) {
            log.error("[KAKAO-LOCAL] coord2address failed. lat={}, lng={}", latitude, longitude, e);
            throw new CustomException(ErrorCode.KAKAO_SEARCH_FAILED);
        }
    }

    /** keyword API 단건 응답 → 내부 DTO. x=longitude, y=latitude (카카오 명세). */
    private PlaceSearchResult toKeywordResult(Map<String, Object> doc) {
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

    /**
     * address API 단건 응답 → 내부 DTO.
     * <p>
     * address API는 documents[]의 최상위에 address_name + x/y가 있고,
     * 도로명 주소는 road_address.address_name + road_address.building_name으로 따로 들어옴.
     * placeName은 빈 값일 수 있어 "건물명 > 도로명 > 지번" 순으로 fallback.
     */
    @SuppressWarnings("unchecked")
    private PlaceSearchResult toAddressResult(Map<String, Object> doc) {
        String addressName = (String) doc.get("address_name");
        Map<String, Object> roadAddress = (Map<String, Object>) doc.get("road_address");

        String roadAddressName = null;
        String buildingName = null;
        if (roadAddress != null) {
            roadAddressName = (String) roadAddress.get("address_name");
            buildingName = (String) roadAddress.get("building_name");
        }

        // 표시용 이름: 건물명 있으면 그거, 없으면 도로명, 없으면 지번
        String placeName;
        if (buildingName != null && !buildingName.isBlank()) {
            placeName = buildingName;
        } else if (roadAddressName != null && !roadAddressName.isBlank()) {
            placeName = roadAddressName;
        } else {
            placeName = addressName;
        }

        return new PlaceSearchResult(
                placeName,
                addressName,
                (roadAddressName != null && !roadAddressName.isBlank()) ? roadAddressName : null,
                Double.parseDouble((String) doc.get("y")),
                Double.parseDouble((String) doc.get("x")),
                "주소"
        );
    }

    /** 좌표 기반 dedup key (소수점 6자리). */
    private String coordKey(PlaceSearchResult r) {
        return String.format("%.6f,%.6f", r.latitude(), r.longitude());
    }

}