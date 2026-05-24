package com.oncare.oncare24.hospital.client;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.oncare.oncare24.hospital.config.NmcProperties;
import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import com.oncare.oncare24.hospital.util.KoreanRegionMapper;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
/**
 * 국립중앙의료원 API 실제 호출 구현체 (병의원 검색).
 * <p>
 * <b>응답 처리</b>:
 * <ul>
 *     <li>QD(진료과) 파라미터는 NMC dgsbjtCd 매핑 검증 이슈로 비활성. 시도(Q0)만으로 검색하고
 *         진료과 필터링은 클라이언트 사이드 (DepartmentNameMatcher)에서 수행.</li>
 *     <li>응답 본문 일부 로깅 — 진단 가시성 확보</li>
 *     <li>ClassCastException 방어 — body/items 노드가 String일 수 있음(에러 응답)</li>
 * </ul>
 *
 * <b>NMC 에러 응답 형식</b>: 정상 응답은 {@code response > body > items > item} 구조이지만,
 * 키 미등록/한도 초과 등에서는 {@code OpenAPI_ServiceResponse > cmmMsgHeader > errMsg} 등을 보냄.
 *
 * <b>활성화</b>: {@code nmc.mock=false}일 때.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "nmc", name = "mock", havingValue = "false", matchIfMissing = false)
public class RealNmcApiClient implements NmcApiClient {

    private static final int NMC_PAGE_SIZE = 500;
    private static final XmlMapper XML_MAPPER = new XmlMapper();

    private final NmcProperties properties;
    private final RestClient restClient;

    public RealNmcApiClient(
            NmcProperties properties,
            @Qualifier("nmcRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public List<HospitalInfo> searchHospitals(
            double latitude, double longitude, int radiusMeters, Department department) {

        List<String> sidos = KoreanRegionMapper.resolveAll(latitude, longitude);
        log.info("[NMC] hospitals search: ({}, {}) → sidos={}, dept={}",
                latitude, longitude, sidos, department);

        // 경계 지역은 좌표가 여러 시도에 동시에 걸침 → 모두 검색 후 병합.
        // 매칭이 없으면 시도 제한 없이 1회 호출.
        List<HospitalInfo> all = new ArrayList<>();
        if (sidos.isEmpty()) {
            all.addAll(callForSido(null));
        } else {
            for (String sido : sidos) {
                all.addAll(callForSido(sido));
            }
        }

        List<HospitalInfo> deduped = dedupeByHpid(all);
        return filterByDistance(deduped, latitude, longitude, radiusMeters);
    }

    /** 시도 1곳(또는 제한 없음) NMC 호출. */
    private List<HospitalInfo> callForSido(String sido) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.hospitalBaseUrl() + "/getHsptlMdcncListInfoInqire")
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("numOfRows", NMC_PAGE_SIZE)
                .queryParam("pageNo", 1);
        if (sido != null) {
            builder.queryParam("Q0", urlEncode(sido));
        }
        URI uri = builder.build(true).toUri();
        return callAndParse(uri);
    }

    /** hpid 기준 중복 제거 (인접 시도 검색 시 동일 병원이 겹칠 수 있음). */
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

            // 응답 본문 일부 로그 - 진단용 (운영 시 제거 가능)
            String preview = xml.length() > 500 ? xml.substring(0, 500) : xml;
            log.info("[NMC] response preview: {}", preview);

            Map<String, Object> root = XML_MAPPER.readValue(xml, Map.class);

            // 에러 응답 처리: cmmMsgHeader 또는 알 수 없는 루트
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

            // body가 Map이 아닌 케이스 (String 등) - 빈 응답 처리
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

            // items가 빈 문자열이면 결과 0개
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
