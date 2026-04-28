package com.oncare.oncare24.hospital.client;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.oncare.oncare24.hospital.config.NmcProperties;
import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import com.oncare.oncare24.location.util.Haversine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 국립중앙의료원 API 실제 호출 구현체.
 * <p>
 * <b>API 명세</b>:
 * <ul>
 *     <li>병의원 - getHsptlMdcncListInfoInqire (응답: XML, UTF-8)</li>
 *     <li>응급실 - getEgytListInfoInqire (응답: XML, UTF-8)</li>
 * </ul>
 *
 * <b>인코딩 주의</b>: NMC API 응답은 UTF-8이지만 Content-Type 헤더에 charset이 명시 안 됐을 때
 * RestClient가 시스템 기본 인코딩으로 디코딩해서 한글이 깨질 수 있다.
 * → byte[]로 받아 UTF-8로 직접 디코딩.
 *
 * <b>거리 필터링</b>: NMC API는 시도/시군구 코드 기반 검색을 권장하지만, 좌표→코드 변환에
 * 별도 코드마스터 API 호출이 필요. 현재는 numOfRows를 크게 잡아 받은 후 클라이언트에서 Haversine 필터링.
 *
 * <b>활성화 조건</b>: {@code nmc.mock=false}일 때.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "nmc", name = "mock", havingValue = "false", matchIfMissing = false)
public class RealNmcApiClient implements NmcApiClient {

    private static final int NMC_PAGE_SIZE = 100;
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

        URI uri = UriComponentsBuilder
                .fromHttpUrl(properties.hospitalBaseUrl() + "/getHsptlMdcncListInfoInqire")
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("numOfRows", NMC_PAGE_SIZE)
                .queryParam("pageNo", 1)
                .build(true)
                .toUri();

        List<HospitalInfo> all = callAndParse(uri, false);
        return filterByDistance(all, latitude, longitude, radiusMeters);
    }

    @Override
    public List<HospitalInfo> searchEmergencyRooms(
            double latitude, double longitude, int radiusMeters) {

        URI uri = UriComponentsBuilder
                .fromHttpUrl(properties.emergencyBaseUrl() + "/getEgytListInfoInqire")
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("numOfRows", NMC_PAGE_SIZE)
                .queryParam("pageNo", 1)
                .build(true)
                .toUri();

        List<HospitalInfo> all = callAndParse(uri, true);
        return filterByDistance(all, latitude, longitude, radiusMeters);
    }

    /**
     * NMC XML 응답 → HospitalInfo 리스트 변환.
     * <p>
     * byte[]로 받아 UTF-8로 직접 디코딩하여 한글 깨짐 방지.
     */
    @SuppressWarnings("unchecked")
    private List<HospitalInfo> callAndParse(URI uri, boolean isEmergency) {
        try {
            byte[] rawBytes = restClient.get().uri(uri).retrieve().body(byte[].class);
            if (rawBytes == null || rawBytes.length == 0) {
                log.warn("[NMC] empty response from {}", uri.getPath());
                return Collections.emptyList();
            }

            String xml = new String(rawBytes, StandardCharsets.UTF_8);

            Map<String, Object> root = XML_MAPPER.readValue(xml, Map.class);
            Map<String, Object> body = (Map<String, Object>) root.get("body");
            if (body == null) {
                log.warn("[NMC] body missing in response");
                return Collections.emptyList();
            }
            Map<String, Object> items = (Map<String, Object>) body.get("items");
            if (items == null) return Collections.emptyList();

            Object itemNode = items.get("item");
            List<Map<String, Object>> rawItems = normalizeItemNode(itemNode);

            return rawItems.stream()
                    .map(item -> mapToHospitalInfo(item, isEmergency))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

        } catch (RestClientException e) {
            log.warn("[NMC] API call failed: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[NMC] failed to parse response", e);
            return Collections.emptyList();
        }
    }

    /**
     * XmlMapper는 단일 item이면 Map으로, 복수면 List로 반환. 항상 List로 정규화.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeItemNode(Object itemNode) {
        if (itemNode == null) return Collections.emptyList();
        if (itemNode instanceof List<?>) return (List<Map<String, Object>>) itemNode;
        if (itemNode instanceof Map<?, ?>) return List.of((Map<String, Object>) itemNode);
        return Collections.emptyList();
    }

    private Optional<HospitalInfo> mapToHospitalInfo(Map<String, Object> item, boolean isEmergency) {
        try {
            String latStr = stringValue(item, "wgs84Lat");
            String lonStr = stringValue(item, "wgs84Lon");
            if (latStr.isBlank() || lonStr.isBlank()) {
                return Optional.empty();
            }
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);

            Integer ercls = null;
            if (isEmergency) {
                String erclsStr = stringValue(item, "dutyEmcls");
                if (!erclsStr.isBlank()) {
                    try {
                        ercls = Integer.parseInt(erclsStr);
                    } catch (NumberFormatException ignored) {}
                }
            }

            return Optional.of(new HospitalInfo(
                    stringValue(item, "hpid"),
                    stringValue(item, "dutyName"),
                    stringValue(item, "dutyAddr"),
                    stringValue(item, "dutyTel1"),
                    lat,
                    lon,
                    stringValue(item, "dgidIdName"),
                    isEmergency,
                    ercls,
                    isEmergency ? null : stringValue(item, "dutyTime1s"),
                    isEmergency ? null : stringValue(item, "dutyTime1c")
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
        return all.stream()
                .filter(h -> Haversine.distance(lat, lon, h.latitude(), h.longitude()) <= radiusMeters)
                .toList();
    }
}