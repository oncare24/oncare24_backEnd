package com.oncare.oncare24.hospital.client;

import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * NMC API 키 발급 전 개발용 Mock 구현체.
 * <p>
 * 사용자 위치 주변에 가짜 병원 5개를 흩뿌려서 반환. 실제 API와 같은 형태의 응답을 만들기 위해
 * 좌표를 사용자 위치 ± 0.005~0.020도 (약 500m~2km 범위) 내에서 생성.
 * <p>
 * application.yml의 {@code nmc.mock=true}일 때 활성화. 키 받으면 {@code false}로 바꾸기만 하면 됨.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "nmc", name = "mock", havingValue = "true", matchIfMissing = false)
public class MockNmcApiClient implements NmcApiClient {

    @Override
    public List<HospitalInfo> searchHospitals(
            double latitude, double longitude, int radiusMeters, Department department) {
        log.info("[Mock NMC] searchHospitals lat={}, lon={}, radius={}m, dept={}",
                latitude, longitude, radiusMeters, department);

        String deptDesc = department != null ? department.getKorean() : "내과";

        return IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new HospitalInfo(
                        "MOCK_HOSP_" + i,
                        "테스트 " + deptDesc + " " + i + "호점",
                        "서울특별시 종로구 테스트로 " + (i * 11),
                        "02-1234-" + String.format("%04d", i * 1111),
                        latitude + (Math.random() - 0.5) * 0.02,
                        longitude + (Math.random() - 0.5) * 0.02,
                        deptDesc,
                        i % 2 == 0 ? "0900" : "0830",
                        i % 2 == 0 ? "1800" : "1730"
                ))
                .toList();
    }
}
