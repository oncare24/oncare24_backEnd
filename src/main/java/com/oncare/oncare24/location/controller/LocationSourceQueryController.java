package com.oncare.oncare24.location.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.location.dto.DeviceStatusSourceResponse;
import com.oncare.oncare24.location.dto.LocationSourceResponse;
import com.oncare.oncare24.location.service.LocationSourceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/wards/{wardId}")
@RequiredArgsConstructor
@Tag(name = "LocationSource", description = "위치 및 디바이스 상태 암호화 원본 조회")
@SecurityRequirement(name = "BearerAuth")
public class LocationSourceQueryController {

    private final LocationSourceQueryService locationSourceQueryService;

    @GetMapping("/location-records/source")
    @Operation(
            summary = "위치 원본 기록 조회",
            description = "encrypted_activity_log에 저장된 암호화 위치 이벤트를 복호화하여 조회합니다. from, to 값을 지정하면 해당 기간의 위치 기록만 조회합니다."
    )
    public ApiResponse<List<LocationSourceResponse>> findLocationRecords(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 피보호자 ID", example = "2")
            @PathVariable Long wardId,
            @Parameter(description = "조회 시작 일시", example = "2026-05-13T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "조회 종료 일시", example = "2026-05-13T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ApiResponse.success(
                locationSourceQueryService.findLocationRecords(
                        userDetails.getUserId(),
                        wardId,
                        from,
                        to
                )
        );
    }

    @GetMapping("/device-status-records/source")
    @Operation(
            summary = "디바이스 상태 원본 기록 조회",
            description = "encrypted_activity_log에 저장된 암호화 디바이스 상태 이벤트를 복호화하여 조회합니다. 미활동 분석에 사용되는 기기 상태 기록을 확인할 수 있습니다."
    )
    public ApiResponse<List<DeviceStatusSourceResponse>> findDeviceStatusRecords(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 피보호자 ID", example = "2")
            @PathVariable Long wardId,
            @Parameter(description = "조회 시작 일시", example = "2026-05-13T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "조회 종료 일시", example = "2026-05-13T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ApiResponse.success(
                locationSourceQueryService.findDeviceStatusRecords(
                        userDetails.getUserId(),
                        wardId,
                        from,
                        to
                )
        );
    }
}
