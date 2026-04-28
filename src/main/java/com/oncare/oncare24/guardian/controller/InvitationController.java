package com.oncare.oncare24.guardian.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.guardian.dto.CreateInvitationRequest;
import com.oncare.oncare24.guardian.dto.SentInvitationResponse;
import com.oncare.oncare24.guardian.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.oncare.oncare24.guardian.dto.ReceivedInvitationResponse;

import java.util.List;

/**
 * 보호자-피보호자 초대 API.
 * <p>
 * <b>9-A 범위 (현재)</b>
 * <ul>
 *     <li>POST   /api/invitations          — 초대 생성</li>
 *     <li>GET    /api/invitations/sent     — 내가 보낸 PENDING 초대 목록</li>
 *     <li>DELETE /api/invitations/{id}     — 보낸 초대 취소(hard delete)</li>
 * </ul>
 * <b>9-B에서 추가 예정</b>: GET /received, POST /{id}/accept, POST /{id}/reject
 */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitation", description = "보호자-피보호자 초대")
@SecurityRequirement(name = "BearerAuth")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "보호자가 어르신에게 초대 발송",
            description = "GUARDIAN 역할만 호출 가능. 입력 wardPhone에 가입된 ELDER에게 SMS 발송. " +
                    "기존 매칭이 ACCEPTED면 G002, PENDING+미만료면 G003 차단. " +
                    "REJECTED나 만료 PENDING은 재초대(row 재사용)."
    )
    public ApiResponse<SentInvitationResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateInvitationRequest request
    ) {
        return ApiResponse.success(invitationService.create(userDetails.getUserId(), request));
    }

    @GetMapping("/sent")
    @Operation(
            summary = "내가 보낸 PENDING 초대 목록",
            description = "보호자 홈에서 '대기 중인 초대' 섹션으로 표시될 데이터. 최신순."
    )
    public ApiResponse<List<SentInvitationResponse>> findAllSent(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(invitationService.findAllSent(userDetails.getUserId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "보낸 초대 취소",
            description = "PENDING 상태이고 본인이 발송한 초대만 취소 가능. hard delete — 재초대 시 새 row."
    )
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        invitationService.cancel(userDetails.getUserId(), id);
        return ApiResponse.success();
    }
    @GetMapping("/received")
    @Operation(
            summary = "내가 받은 PENDING 초대 목록",
            description = "ELDER 역할만 호출 가능. 만료된 초대는 자동으로 숨김. 최신순."
    )
    public ApiResponse<List<ReceivedInvitationResponse>> findAllReceived(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(invitationService.findAllReceived(userDetails.getUserId()));
    }

    @PostMapping("/{id}/accept")
    @Operation(
            summary = "받은 초대 수락",
            description = "ELDER 역할 + 본인이 받은 초대 + PENDING + 미만료 4가지 모두 통과해야 ACCEPTED로 전이. " +
                    "이후 보호자가 SafetyZone/위치모니터링 권한을 행사할 수 있음."
    )
    public ApiResponse<ReceivedInvitationResponse> accept(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ApiResponse.success(invitationService.accept(userDetails.getUserId(), id));
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "받은 초대 거절",
            description = "REJECTED로 전이. 보호자가 다시 같은 어르신에게 초대를 보내면 row를 재사용(reInvite)."
    )
    public ApiResponse<Void> reject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        invitationService.reject(userDetails.getUserId(), id);
        return ApiResponse.success();
    }
}