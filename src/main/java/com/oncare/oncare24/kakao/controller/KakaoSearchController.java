package com.oncare.oncare24.kakao.controller;

import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.kakao.client.KakaoLocalClient;
import com.oncare.oncare24.kakao.dto.PlaceSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.oncare.oncare24.kakao.dto.ReverseGeocodeResponse;

import java.math.BigDecimal;
/**
 * 카카오 로컬 API 프록시.
 * <p>
 * <b>왜 프록시인가</b>: REST API 키를 클라이언트에 노출하지 않기 위함. 모든 카카오 호출은 백엔드 경유.
 * 키 회전·요청량 모니터링·캐싱 정책을 한 곳에서 관리할 수 있다는 부가 이점도 있음.
 *
 * <b>인증</b>: 일반 사용자(ELDER/GUARDIAN) 모두 호출 가능. 안전구역 등록 흐름에서 사용.
 *
 * <b>통합 검색</b>: keyword + address API 동시 호출하여 합쳐서 반환. 카카오맵 자체 검색과 동일 UX.
 */
@RestController
@RequestMapping("/api/kakao")
@RequiredArgsConstructor
@Validated
@Tag(name = "KakaoSearch", description = "카카오 로컬 검색 프록시")
@SecurityRequirement(name = "BearerAuth")
public class KakaoSearchController {

    private final KakaoLocalClient kakaoLocalClient;

    @GetMapping("/search")
    @Operation(
            summary = "장소/주소 통합 검색",
            description = "카카오 로컬 keyword + address API 통합 검색. 장소명·랜드마크·도로명·지번 모두 검색 가능. " +
                    "예) '양산역', '삼성병원', '경상남도 양산시 중앙로 39'."
    )
    public ApiResponse<PlaceSearchResponse> search(
            @RequestParam @NotBlank @Size(min = 1, max = 80) String query,
            @RequestParam(defaultValue = "1") @Min(1) @Max(45) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(15) int size
    ) {
        return ApiResponse.success(kakaoLocalClient.search(query, page, size));
    }

    @GetMapping("/coord2address")
    @Operation(
            summary = "좌표 → 주소 변환",
            description = "지도 핀이 멈춘 좌표를 도로명/지번 주소로 변환. " +
                    "안전구역 등록/수정 화면에서 핀 이동 시 자동 호출되어 주소 입력란을 채움. " +
                    "도로명이 있으면 우선, 없으면 지번. 해상·산간 좌표는 둘 다 null 가능."
    )
    public ApiResponse<ReverseGeocodeResponse> coord2address(
            @RequestParam @NotNull
            @DecimalMin(value = "-90.0", message = "위도 범위가 올바르지 않아요")
            @DecimalMax(value = "90.0", message = "위도 범위가 올바르지 않아요")
            BigDecimal latitude,

            @RequestParam @NotNull
            @DecimalMin(value = "-180.0", message = "경도 범위가 올바르지 않아요")
            @DecimalMax(value = "180.0", message = "경도 범위가 올바르지 않아요")
            BigDecimal longitude
    ) {
        return ApiResponse.success(kakaoLocalClient.coord2address(latitude, longitude));
    }
}