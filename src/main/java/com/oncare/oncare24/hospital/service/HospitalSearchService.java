package com.oncare.oncare24.hospital.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.hospital.client.NmcApiClient;
import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import com.oncare.oncare24.hospital.util.KoreanRegionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * NMC API 검색 + Redis 캐싱.
 * <p>
 * <b>캐시 키 구조</b>: {@code hospital:{type}:{sido}:{deptCode}}
 * <p>
 * 좌표가 아닌 <b>시도 단위</b>로 캐시 → 같은 도(道) 사용자 모두가 캐시 공유.
 * 서울 사용자 1명이 호출하면 다른 서울 사용자들 모두 캐시 히트.
 * <p>
 * <b>거리 필터링은 캐시 후 적용</b>: NmcApiClient가 시도 단위로 받은 후 클라이언트에서 거리 필터링.
 * 캐시는 시도 전체 데이터를 저장하므로 사용자별로 다른 반경에도 같은 캐시 사용 가능.
 * <p>
 * 다만 현재 구현은 NmcApiClient 안에서 거리 필터링까지 마친 결과를 캐시한다 (단순화).
 * 같은 시도 + 같은 반경 + 같은 좌표 격자(100m) 조합 캐시 키로 둠.
 *
 * <p><b>TTL</b>: 1시간. 병원 영업 정보는 자주 안 바뀜.
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
     * 캐시 키 = {@code hospital:{type}:{시도}_{반경km}:{좌표격자}:{진료과}}
     * <p>
     * 시도가 같고 반경/좌표격자가 같으면 캐시 공유. 같은 도(道) 사용자끼리 캐시 효율 ↑.
     * 시도 매핑 실패 시 "UNKNOWN".
     */
    private String buildKey(String type, double lat, double lon, int radius, String suffix) {
        String sido = KoreanRegionMapper.resolve(lat, lon);
        String region = sido != null ? sido : "UNKNOWN";
        return String.format("hospital:%s:%s:%dkm:%.3f:%.3f:%s",
                type, region, radius / 1000, lat, lon, suffix);
    }
}
