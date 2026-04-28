package com.oncare.oncare24.hospital.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.hospital.client.NmcApiClient;
import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * NMC API 검색 + Redis 캐싱.
 * <p>
 * <b>캐시 키 구조</b>: {@code hospital:{type}:{lat100m_grid}:{lon100m_grid}:{radius}:{deptCode}}
 * <p>
 * 좌표를 100m 격자로 라운딩하여 같은 동네 사용자끼리 캐시 공유. 위/경도 0.001도 ≈ 110m이므로
 * latitude/longitude를 소수점 3자리에서 자른다.
 * <p>
 * <b>TTL</b>: 1시간. 병원 영업 정보는 자주 안 바뀜. 응급실 등 실시간성 필요한 데이터는 별도 처리 검토.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalSearchService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final TypeReference<List<HospitalInfo>> HOSPITAL_LIST_TYPE = new TypeReference<>() {};

    private final NmcApiClient nmcApiClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public List<HospitalInfo> searchHospitals(
            double latitude, double longitude, int radiusMeters, Department department) {

        String key = buildKey("hosp", latitude, longitude, radiusMeters,
                department != null ? department.getCode() : "ALL");

        return getOrFetch(key, () ->
                nmcApiClient.searchHospitals(latitude, longitude, radiusMeters, department));
    }

    public List<HospitalInfo> searchEmergencyRooms(
            double latitude, double longitude, int radiusMeters) {

        String key = buildKey("er", latitude, longitude, radiusMeters, "ER");
        return getOrFetch(key, () ->
                nmcApiClient.searchEmergencyRooms(latitude, longitude, radiusMeters));
    }

    /**
     * Redis에 캐시된 값이 있으면 반환, 없으면 fetcher 실행 후 저장.
     */
    private List<HospitalInfo> getOrFetch(String key, java.util.function.Supplier<List<HospitalInfo>> fetcher) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("[HospitalSearch] cache hit: {}", key);
                return objectMapper.readValue(cached, HOSPITAL_LIST_TYPE);
            }
        } catch (Exception e) {
            log.warn("[HospitalSearch] cache read failed (key={}): {}", key, e.getMessage());
        }

        List<HospitalInfo> fresh = fetcher.get();

        try {
            String json = objectMapper.writeValueAsString(fresh);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
            log.debug("[HospitalSearch] cache set: {} (size={})", key, fresh.size());
        } catch (Exception e) {
            log.warn("[HospitalSearch] cache write failed (key={}): {}", key, e.getMessage());
        }
        return fresh;
    }

    /**
     * 위/경도를 100m 격자로 라운딩한 캐시 키 생성. 같은 격자 안 사용자끼리 캐시 공유.
     */
    private String buildKey(String type, double lat, double lon, int radius, String suffix) {
        return String.format("hospital:%s:%.3f:%.3f:%d:%s", type, lat, lon, radius, suffix);
    }
}
