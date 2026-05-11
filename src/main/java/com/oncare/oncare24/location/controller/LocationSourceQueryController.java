package com.oncare.oncare24.location.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.location.dto.DeviceStatusSourceResponse;
import com.oncare.oncare24.location.dto.LocationSourceResponse;
import com.oncare.oncare24.location.service.LocationSourceQueryService;
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
@Tag(name = "LocationSource", description = "Encrypted location and device source queries")
@SecurityRequirement(name = "BearerAuth")
public class LocationSourceQueryController {

    private final LocationSourceQueryService locationSourceQueryService;

    @GetMapping("/location-records/source")
    public ApiResponse<List<LocationSourceResponse>> findLocationRecords(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
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
    public ApiResponse<List<DeviceStatusSourceResponse>> findDeviceStatusRecords(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
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
