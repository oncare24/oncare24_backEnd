package com.oncare.oncare24.medication.controller;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.medication.dto.CreateMedicationGroupRequest;
import com.oncare.oncare24.medication.dto.MedicationGroupListResponse;
import com.oncare.oncare24.medication.dto.MedicationGroupResponse;
import com.oncare.oncare24.medication.dto.MedicationPacketCreateRequest;
import com.oncare.oncare24.medication.dto.MovePacketTimeRequest;
import com.oncare.oncare24.medication.dto.TodayMedicationResponse;
import com.oncare.oncare24.medication.dto.UpdateGroupNameRequest;
import com.oncare.oncare24.medication.dto.UpdatePacketRequest;
import com.oncare.oncare24.medication.service.MedicationGroupCommandService;
import com.oncare.oncare24.medication.service.MedicationGroupQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 봉지(DoseGroup) 모델 API — 4장 계약.
 * <ul>
 *   <li>GET .../medication-schedules/source — 봉지 계층 조회(4-1)</li>
 *   <li>GET .../medication-schedules/today  — 오늘의 약(4-2)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/wards/{wardId}")
@RequiredArgsConstructor
@Tag(name = "MedicationGroup", description = "복약 봉지(DoseGroup) 모델")
@SecurityRequirement(name = "BearerAuth")
public class MedicationGroupController {

    private final MedicationGroupQueryService medicationGroupQueryService;
    private final MedicationGroupCommandService medicationGroupCommandService;

    @GetMapping("/medication-schedules/source")
    @Operation(
            summary = "복약 일정 봉지 계층 조회 (4-1)",
            description = "암호화 원천을 복호화해 groupId→봉지(시각)→성분 계층으로 반환합니다. "
                    + "AUTO는 봉지별 성분 리스트, MANUAL은 약 단위."
    )
    public ApiResponse<MedicationGroupListResponse> findGroups(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 피보호자 ID", example = "2")
            @PathVariable Long wardId,
            @Parameter(description = "비활성화된 봉지 포함 여부", example = "false")
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ApiResponse.success(
                medicationGroupQueryService.findGroups(
                        userDetails.getUserId(),
                        wardId,
                        includeInactive
                )
        );
    }

    @GetMapping("/medication-schedules/today")
    @Operation(
            summary = "오늘의 약 조회 (4-2)",
            description = "해당 날짜에 유효한 일정을 시각(슬롯) 단위로 묶고, 성분별 복용 상태를 함께 반환합니다."
    )
    public ApiResponse<TodayMedicationResponse> findToday(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 피보호자 ID", example = "2")
            @PathVariable Long wardId,
            @Parameter(description = "조회할 날짜", example = "2026-06-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(
                medicationGroupQueryService.findToday(
                        userDetails.getUserId(),
                        wardId,
                        date
                )
        );
    }

    @PutMapping("/medication-schedules/groups/{groupId}/packets/time")
    @Operation(
            summary = "봉지 시각 이동 (4-3)",
            description = "(groupId, fromTime)의 모든 성분 row를 한 번에 toTime으로 이동합니다. "
                    + "한 줄만 옮겨 옛 시간이 잔존하는 버그를 구조적으로 차단."
    )
    public ApiResponse<Void> movePacketTime(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @Parameter(description = "봉지 식별자", example = "codef:rx:Rx20260601-001")
            @PathVariable String groupId,
            @Valid @RequestBody MovePacketTimeRequest request
    ) {
        medicationGroupCommandService.movePacketTime(
                userDetails.getUserId(), wardId, groupId, request.fromTime(), request.toTime());
        return ApiResponse.success();
    }

    @PutMapping("/medication-schedules/groups/{groupId}/packets/{scheduledTime}")
    @Operation(
            summary = "봉지 속성(요일/기간) 변경 (4-4)",
            description = "(groupId, scheduledTime) 봉지의 일정 유형·요일·기간을 변경합니다."
    )
    public ApiResponse<Void> updatePacket(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @Parameter(description = "봉지 식별자", example = "manual:6b1f-ab")
            @PathVariable String groupId,
            @Parameter(description = "대상 봉지 시각", example = "08:00:00")
            @PathVariable String scheduledTime,
            @Valid @RequestBody UpdatePacketRequest request
    ) {
        medicationGroupCommandService.updatePacket(
                userDetails.getUserId(), wardId, groupId, LocalTime.parse(scheduledTime), request);
        return ApiResponse.success();
    }

    @PostMapping("/medication-schedules/groups")
    @Operation(
            summary = "수동 봉지(약) 생성 (4-5)",
            description = "한 약을 한 봉지(group)로 등록합니다. packets의 시각마다 일정이 생성됩니다."
    )
    public ApiResponse<MedicationGroupResponse> createGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @Valid @RequestBody CreateMedicationGroupRequest request
    ) {
        return ApiResponse.success(
                medicationGroupCommandService.createGroup(userDetails.getUserId(), wardId, request));
    }

    @DeleteMapping("/medication-schedules/groups/{groupId}")
    @Operation(
            summary = "봉지(처방/약) 통째 삭제 (4-6)",
            description = "group의 모든 활성 일정을 비활성화합니다."
    )
    public ApiResponse<Void> deleteGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @Parameter(description = "봉지 식별자", example = "codef:rx:Rx20260601-001")
            @PathVariable String groupId
    ) {
        medicationGroupCommandService.deleteGroup(userDetails.getUserId(), wardId, groupId);
        return ApiResponse.success();
    }

    @DeleteMapping("/medication-schedules/groups/{groupId}/packets/{scheduledTime}")
    @Operation(
            summary = "특정 봉지(시각)만 삭제 (4-6)",
            description = "(groupId, scheduledTime) 봉지의 일정만 비활성화합니다."
    )
    public ApiResponse<Void> deletePacket(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @Parameter(description = "봉지 식별자", example = "codef:rx:Rx20260601-001")
            @PathVariable String groupId,
            @Parameter(description = "대상 봉지 시각", example = "08:00:00")
            @PathVariable String scheduledTime
    ) {
        medicationGroupCommandService.deletePacket(
                userDetails.getUserId(), wardId, groupId, LocalTime.parse(scheduledTime));
        return ApiResponse.success();
    }

    @PostMapping("/medication-schedules/groups/{groupId}/packets")
    @Operation(
            summary = "봉지에 시각(packet) 추가 (MANUAL 전용)",
            description = "기존 봉지에 새 복용 시각을 추가한다. 약명은 봉지의 기존 약명을 상속. "
                    + "AUTO(CODEF 자동) 봉지는 편집 불가(M005)."
    )
    public ApiResponse<Void> addPacket(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @Parameter(description = "봉지 식별자", example = "manual:6b1f-ab")
            @PathVariable String groupId,
            @Valid @RequestBody MedicationPacketCreateRequest request
    ) {
        medicationGroupCommandService.addPacket(userDetails.getUserId(), wardId, groupId, request);
        return ApiResponse.success();
    }

    @PatchMapping("/medication-schedules/groups/{groupId}")
    @Operation(
            summary = "봉지 이름 변경 (MANUAL 전용)",
            description = "봉지(약)의 이름을 변경한다. AUTO(CODEF 자동) 봉지는 편집 불가(M005)."
    )
    public ApiResponse<Void> renameGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long wardId,
            @Parameter(description = "봉지 식별자", example = "manual:6b1f-ab")
            @PathVariable String groupId,
            @Valid @RequestBody UpdateGroupNameRequest request
    ) {
        medicationGroupCommandService.renameGroup(
                userDetails.getUserId(), wardId, groupId, request.medicationName());
        return ApiResponse.success();
    }
}
