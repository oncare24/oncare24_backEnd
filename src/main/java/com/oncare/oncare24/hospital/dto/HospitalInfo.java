package com.oncare.oncare24.hospital.dto;

/**
 * 외부 NMC API 응답을 정규화한 병원/응급실 정보.
 * <p>
 * NMC API는 2종류:
 * <ul>
 *     <li>전국 병의원 찾기 - {@code dutyName, dutyAddr, wgs84Lat, wgs84Lon, dutyTel1, dutyTime1s ~ dutyTime8s}</li>
 *     <li>전국 응급의료기관 - {@code dutyName, dutyAddr, wgs84Lat, wgs84Lon, dutyTel1, dutyEmcls (응급실 등급)}</li>
 * </ul>
 * 이 record는 두 응답을 통합한 형태.
 *
 * @param hpid                   NMC 병원 고유 ID (재호출 캐싱 키로 사용)
 * @param name                   병원명
 * @param address                주소
 * @param tel                    대표 전화번호
 * @param latitude               위도
 * @param longitude              경도
 * @param departmentDescription  진료과 정보 (NMC 응답 그대로, 자유 텍스트)
 * @param isEmergency            응급의료기관 여부
 * @param emergencyClass         응급실 등급 (1: 권역응급의료센터, 2: 지역응급의료센터, 3: 지역응급의료기관). 일반 병원이면 null
 * @param weekdayOpenTime        평일 영업 시작 (HHmm. 예: 0900). 정보 없으면 null
 * @param weekdayCloseTime       평일 영업 종료 (HHmm. 예: 1800). 정보 없으면 null
 */
public record HospitalInfo(
        String hpid,
        String name,
        String address,
        String tel,
        double latitude,
        double longitude,
        String departmentDescription,
        boolean isEmergency,
        Integer emergencyClass,
        String weekdayOpenTime,
        String weekdayCloseTime
) {
}
