package com.oncare.oncare24.safetyzone.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.safetyzone.dto.CreateSafetyZoneRequest;
import com.oncare.oncare24.safetyzone.dto.SafetyZoneResponse;
import com.oncare.oncare24.safetyzone.dto.UpdateNotificationRequest;
import com.oncare.oncare24.safetyzone.dto.UpdateSafetyZoneRequest;
import com.oncare.oncare24.safetyzone.service.SafetyZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 안전구역 API.
 * <p>
 * 모든 엔드포인트 인증 필수. 권한 검증은 Service 레이어에서 3단계로 수행.
 * <p>
 * <b>엔드포인트</b>
 * <ul>
 *     <li>POST   /api/safety-zones                       — 등록</li>
 *     <li>GET    /api/safety-zones?wardId={id}           — 피보호자별 목록 조회</li>
 *     <li>GET    /api/safety-zones/{id}                  — 단건 조회</li>
 *     <li>PUT    /api/safety-zones/{id}                  — 전체 수정 (이름/타입/주소/좌표/반경)</li>
 *     <li>PATCH  /api/safety-zones/{id}/notification     — 알림 토글 단일 갱신</li>
 *     <li>DELETE /api/safety-zones/{id}                  — soft delete</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/safety-zones")
@RequiredArgsConstructor
@Tag(name = "SafetyZone", description = "안전 구역 관리")
@SecurityRequirement(name = "BearerAuth")
public class SafetyZoneController {

    private final SafetyZoneService safetyZoneService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "안전구역 등록",
            description = "보호자가 자신과 ACCEPTED 상태로 연동된 피보호자에 대해 안전구역을 등록합니다. " +
                    "한 피보호자당 최대 5개. 등록 직후 알림은 ON 상태."
    )
    public ApiResponse<SafetyZoneResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateSafetyZoneRequest request
    ) {
        return ApiResponse.success(safetyZoneService.create(userDetails.getUserId(), request));
    }

    @GetMapping
    @Operation(
            summary = "안전구역 목록 조회",
            description = "특정 피보호자(wardId)의 활성 안전구역 전체를 등록순으로 반환합니다. " +
                    "같은 피보호자에 연동된 다른 보호자가 등록한 zone도 함께 보입니다."
    )
    public ApiResponse<List<SafetyZoneResponse>> findAllByWard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long wardId
    ) {
        return ApiResponse.success(safetyZoneService.findAllByWard(userDetails.getUserId(), wardId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "안전구역 단건 조회")
    public ApiResponse<SafetyZoneResponse> findById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ApiResponse.success(safetyZoneService.findById(userDetails.getUserId(), id));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "안전구역 수정",
            description = "이름/타입/주소/좌표/반경을 통째로 갱신. 본인이 등록한 zone만 수정 가능."
    )
    public ApiResponse<SafetyZoneResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSafetyZoneRequest request
    ) {
        return ApiResponse.success(safetyZoneService.update(userDetails.getUserId(), id, request));
    }

    @PatchMapping("/{id}/notification")
    @Operation(
            summary = "안전구역 알림 토글",
            description = "이탈 알림 ON/OFF만 단일 갱신. 본인이 등록한 zone만 변경 가능."
    )
    public ApiResponse<SafetyZoneResponse> updateNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateNotificationRequest request
    ) {
        return ApiResponse.success(
                safetyZoneService.updateNotification(userDetails.getUserId(), id, request)
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "안전구역 삭제",
            description = "soft delete. is_active=false 처리. 본인이 등록한 zone만 삭제 가능."
    )
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        safetyZoneService.softDelete(userDetails.getUserId(), id);
        return ApiResponse.success();
    }
}