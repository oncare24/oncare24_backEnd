package com.oncare.oncare24.kakao.dto;

/**
 * 좌표 → 행정구역 변환 결과. NMC 검색의 Q0(시도)/Q1(시군구) 파라미터로 사용.
 */
public record RegionCode(String sido, String sigungu) {
}