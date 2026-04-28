package com.oncare.oncare24.hospital.client;

import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.HospitalInfo;

import java.util.List;

/**
 * 국립중앙의료원(NMC) Open API 추상화.
 * <p>
 * 두 구현체:
 * <ul>
 *     <li>{@link MockNmcApiClient} - 키 발급 전 개발용. 가짜 데이터 반환.</li>
 *     <li>{@link RealNmcApiClient} - 실제 NMC API 호출.</li>
 * </ul>
 * application.yml의 {@code nmc.mock} 값에 따라 둘 중 하나만 활성화 ({@code @ConditionalOnProperty}).
 *
 * <b>호출자 입장</b>: 어떤 구현이 동작 중인지 신경 쓸 필요 없음. 동일 인터페이스로 사용.
 */
public interface NmcApiClient {

    /**
     * 위치 기반 일반 병의원 검색.
     *
     * @param latitude     사용자 위도
     * @param longitude    사용자 경도
     * @param radiusMeters 검색 반경(미터)
     * @param department   진료과 필터 (null이면 모든 진료과)
     * @return 병원 리스트. 못 찾으면 빈 리스트.
     */
    List<HospitalInfo> searchHospitals(
            double latitude, double longitude, int radiusMeters, Department department);

    /**
     * 위치 기반 응급의료기관 검색.
     *
     * @param latitude     사용자 위도
     * @param longitude    사용자 경도
     * @param radiusMeters 검색 반경(미터)
     * @return 응급의료기관 리스트. 못 찾으면 빈 리스트.
     */
    List<HospitalInfo> searchEmergencyRooms(
            double latitude, double longitude, int radiusMeters);
}
