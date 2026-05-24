package com.oncare.oncare24.drugsafety.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import com.oncare.oncare24.drugsafety.dto.PrescriptionDto;
/**
 * 캐시된 분석 결과 조회 응답.
 * <p>
 * 보호자/피보호자 화면에서 공통으로 사용.
 * analyzedAt 은 "마지막 업데이트" 표시 및 30일 경과 배너 트리거에 사용.
 * 분석 이력이 없으면 컨트롤러에서 null payload 또는 404로 처리한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedicationAnalysisResponse {
    private List<WarningDto> warnings;
    private List<PrescriptionDto> prescriptions;
    private LocalDateTime analyzedAt;
    private com.oncare.oncare24.medication.dto.AutoRegisterResult autoRegisterResult;   // ← 추가

}