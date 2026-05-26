package com.oncare.oncare24.hospital.client;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.oncare.oncare24.hospital.config.NmcProperties;
import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import com.oncare.oncare24.hospital.util.KoreanRegionMapper;
import com.oncare.oncare24.kakao.client.KakaoLocalClient;
import com.oncare.oncare24.kakao.dto.RegionCode;
import com.oncare.oncare24.location.util.Haversine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 국립중앙의료원 API 실제 호출 구현체 (병의원 검색).
 * <p>
 * <b>검색 범위</b>: 사용자 좌표 + 반경 테두리가 걸치는 시군구(Q1) 단위로만 호출한다.
 * NMC 목록 API는 좌표/반경 검색을 지원하지 않아 지역 단위로 받아 거리로 거르는데,
 * 시도(Q0) 전체를 받으면 결과가 수천 건이라 페이지 상한(500)에 가까운 병원이 잘려 누락된다.
 * 좌표를 카카오로 시군구까지 역지오코딩해 동네 단위로 좁히면 누락 구조 자체가 사라진다.
 *
 * <p><b>진료과(QD)</b>는 NMC dgsbjtCd 매핑 이슈로 비활성 — 진료과 필터링은 DepartmentNameMatcher(이름 매칭).
 * <b>활성화</b>: {@code nmc.mock=false}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "nmc", name = "mock", havingValue = "false", matchIfMissing = false)
public class RealNmcApiClient implements NmcApiClient {

    private static final int NMC_PAGE_SIZE = 500;
    private static final XmlMapper XML_MAPPER = new XmlMapper();

    private final NmcProperties properties;
    private final RestClient restClient;
    private final KakaoLocalClient kakaoLocalClient;

    public RealNmcApiClient(
            NmcProperties properties,
            @Qualifier("nmcRestClient") RestClient restClient,
            KakaoLocalClient kakaoLocalClient
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.kakaoLocalClient = kakaoLocalClient;
    }

    @Override
    public List<HospitalInfo> searchHospitals(
            double latitude, double longitude, int radiusMeters, Department department) {

        // 좌표 + 반경 테두리가 걸치는 시군구들을 모아 동네 단위로만 NMC 호출.
        Set<RegionCode> regions = resolveRegions(latitude, longitude, radiusMeters);
        log.info("[NMC] hospitals search: ({}, {}) r={}m → regions={}, dept={}",
                latitude, longitude, radiusMeters, regions, department);

        List<HospitalInfo> all = new ArrayList<>();
        for (RegionCode region : regions) {
            all.addAll(callForRegion(region.sido(), region.sigungu()));
        }

        // 안전망: 시군구 검색이 비면(카카오 실패 / Q0·Q1 형식 불일치 등) 기존 시도 단위로 폴백.
        if (all.isEmpty()) {
            log.warn("[NMC] sigungu search empty → fallback to sido search");
            List<String> sidos = KoreanRegionMapper.resolveAll(latitude, longitude);
            if (sidos.isEmpty()) {
                all.addAll(callForRegion(null, null));
            } else {
                for (String sido : sidos) {
                    all.addAll(callForRegion(sido, null));
                }
            }
        }

        List<HospitalInfo> deduped = dedupeByHpid(all);
        return filterByDistance(deduped, latitude, longitude, radiusMeters);
    }

    /** 중심 + 반경 테두리 8방위를 역지오코딩 → 걸치는 시군구 집합 (경계면 옆 동네 자동 포함). */
    private Set<RegionCode> resolveRegions(double lat, double lon, int radiusMeters) {
        Set<RegionCode> regions = new LinkedHashSet<>();
        addRegion(regions, lat, lon); // 중심

        double metersPerDegLat = 111_320.0;
        double metersPerDegLon = 111_320.0 * Math.cos(Math.toRadians(lat));
        for (int deg = 0; deg < 360; deg += 45) {
            double rad = Math.toRadians(deg);
            double dLat = (radiusMeters * Math.cos(rad)) / metersPerDegLat;
            double dLon = (radiusMeters * Math.sin(rad)) / metersPerDegLon;
            addRegion(regions, lat + dLat, lon + dLon);
        }
        return regions;
    }

    private void addRegion(Set<RegionCode> regions, double lat, double lon) {
        RegionCode r = kakaoLocalClient.coord2region(lat, lon);
        if (r != null && r.sigungu() != null) {
            regions.add(r);
        }
    }

    /** 시군구(Q1) 단위 NMC 호출. sigungu가 null이면 시도(Q0)만으로 호출(폴백용). */
    private List<HospitalInfo> callForRegion(String sido, String sigungu) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.hospitalBaseUrl() + "/getHsptlMdcncListInfoInqire")
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("numOfRows", NMC_PAGE_SIZE)
                .queryParam("pageNo", 1);
        if (sido != null) {
            builder.queryParam("Q0", urlEncode(sido));
        }
        if (sigungu != null) {
            builder.queryParam("Q1", urlEncode(sigungu));
        }
        URI uri = builder.build(true).toUri();
        return callAndParse(uri);
    }

    /** hpid 기준 중복 제거 (인접 시군구 검색 시 동일 병원이 겹칠 수 있음). */
    private List<HospitalInfo> dedupeByHpid(List<HospitalInfo> list) {
        Map<String, HospitalInfo> byId = new LinkedHashMap<>();
        for (HospitalInfo h : list) {
            byId.putIfAbsent(h.hpid(), h);
        }
        return new ArrayList<>(byId.values());
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @SuppressWarnings("unchecked")
    private List<HospitalInfo> callAndParse(URI uri) {
        try {
            byte[] rawBytes = restClient.get().uri(uri).retrieve().body(byte[].class);
            if (rawBytes == null || rawBytes.length == 0) {
                log.warn("[NMC] empty response from {}", uri.getPath());
                return Collections.emptyList();
            }

            String xml = new String(rawBytes, StandardCharsets.UTF_8);

            String preview = xml.length() > 500 ? xml.substring(0, 500) : xml;
            log.info("[NMC] response preview: {}", preview);

            Map<String, Object> root = XML_MAPPER.readValue(xml, Map.class);

            Object bodyNode = root.get("body");
            if (bodyNode == null) {
                Object headerNode = root.get("cmmMsgHeader");
                if (headerNode != null) {
                    log.warn("[NMC] API error response: {}", headerNode);
                } else {
                    log.warn("[NMC] unexpected response structure: keys={}", root.keySet());
                }
                return Collections.emptyList();
            }

            if (!(bodyNode instanceof Map<?, ?>)) {
                log.warn("[NMC] body is not a Map: type={}, value={}",
                        bodyNode.getClass().getSimpleName(), bodyNode);
                return Collections.emptyList();
            }

            Map<String, Object> body = (Map<String, Object>) bodyNode;

            Object totalCount = body.get("totalCount");
            log.info("[NMC] totalCount={}", totalCount);

            Object itemsNode = body.get("items");
            if (itemsNode == null) {
                log.warn("[NMC] items node missing in body");
                return Collections.emptyList();
            }

            if (itemsNode instanceof String) {
                log.info("[NMC] items is empty string (no results)");
                return Collections.emptyList();
            }

            if (!(itemsNode instanceof Map<?, ?>)) {
                log.warn("[NMC] items is not a Map: type={}", itemsNode.getClass().getSimpleName());
                return Collections.emptyList();
            }

            Map<String, Object> items = (Map<String, Object>) itemsNode;

            Object itemNode = items.get("item");
            List<Map<String, Object>> rawItems = normalizeItemNode(itemNode);

            List<HospitalInfo> result = rawItems.stream()
                    .map(this::mapToHospitalInfo)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

            log.info("[NMC] parsed {} items from {} raw entries", result.size(), rawItems.size());
            return result;

        } catch (RestClientException e) {
            log.warn("[NMC] API call failed: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[NMC] failed to parse response", e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeItemNode(Object itemNode) {
        if (itemNode == null) return Collections.emptyList();
        if (itemNode instanceof List<?>) return (List<Map<String, Object>>) itemNode;
        if (itemNode instanceof Map<?, ?>) return List.of((Map<String, Object>) itemNode);
        return Collections.emptyList();
    }

    private Optional<HospitalInfo> mapToHospitalInfo(Map<String, Object> item) {
        try {
            String latStr = stringValue(item, "wgs84Lat");
            String lonStr = stringValue(item, "wgs84Lon");
            if (latStr.isBlank() || lonStr.isBlank()) {
                return Optional.empty();
            }
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);

            return Optional.of(new HospitalInfo(
                    stringValue(item, "hpid"),
                    stringValue(item, "dutyName"),
                    stringValue(item, "dutyAddr"),
                    stringValue(item, "dutyTel1"),
                    lat,
                    lon,
                    stringValue(item, "dgidIdName"),
                    stringValue(item, "dutyTime1s"),
                    stringValue(item, "dutyTime1c")
            ));
        } catch (Exception e) {
            log.debug("[NMC] skip malformed item: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString();
    }

    private List<HospitalInfo> filterByDistance(
            List<HospitalInfo> all, double lat, double lon, int radiusMeters) {
        List<HospitalInfo> filtered = all.stream()
                .filter(h -> Haversine.distance(lat, lon, h.latitude(), h.longitude()) <= radiusMeters)
                .toList();
        log.info("[NMC] after distance filter ({}m): {} → {}",
                radiusMeters, all.size(), filtered.size());
        return filtered;
    }
}