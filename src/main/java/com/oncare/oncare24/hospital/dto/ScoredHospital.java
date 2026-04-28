package com.oncare.oncare24.hospital.dto;

/**
 * 사용자 위치 기준으로 점수가 계산된 병원.
 * <p>
 * 응답에 그대로 직렬화되어 클라이언트로 전달됨.
 *
 * @param name             병원명
 * @param address          주소
 * @param tel              대표 전화
 * @param latitude         위도
 * @param longitude        경도
 * @param distanceMeters   사용자 위치로부터의 직선 거리(미터). Haversine 공식.
 * @param isOpenNow        현재 시각에 영업 중인지 여부. 정보가 없으면 null
 * @param isEmergency      응급의료기관 여부
 * @param emergencyClass   응급실 등급 (응급 케이스에서만 의미 있음)
 * @param score            정렬용 종합 점수 (높을수록 추천. 거리 + 영업중 + 응급등급 가중합)
 */
public record ScoredHospital(
        String name,
        String address,
        String tel,
        double latitude,
        double longitude,
        int distanceMeters,
        Boolean isOpenNow,
        boolean isEmergency,
        Integer emergencyClass,
        double score
) {
}
