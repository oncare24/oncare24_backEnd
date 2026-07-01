package com.oncare.oncare24.medication.dto;

import com.oncare.oncare24.medication.entity.MedicationSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 복용 묶음(DoseGroup). AUTO=처방 단위(약명 null), MANUAL=약 단위(약명 존재). */
public record MedicationGroupResponse(
        @Schema(description = "봉지(DoseGroup) 식별자", example = "codef:rx:Rx20260601-001")
        String groupId,
        @Schema(description = "출처 (AUTO=CODEF 자동, MANUAL=수동)", example = "AUTO")
        MedicationSource source,
        @Schema(description = "약 이름(MANUAL만). AUTO는 봉지라 단일 약명 없음 → null", example = "복통약")
        String medicationName,
        @Schema(description = "봉지(시각) 목록")
        List<MedicationPacketResponse> packets
) {
}
