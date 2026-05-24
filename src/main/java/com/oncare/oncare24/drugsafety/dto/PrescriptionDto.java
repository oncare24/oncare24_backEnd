package com.oncare.oncare24.drugsafety.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Graph RAG 서버에서 반환하는 처방받은 약 정보.
 * CODEF 원본 필드명(resXxx) 유지. 일부 필드는 누락 가능(null 허용).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrescriptionDto {
    private String resDrugName;             // 약 이름
    private String resIngredients;          // 성분명
    private String resPrescribeDrugEffect;  // 효능 (optional)
    private String resContent;              // 함량
    private String resOneDose;              // 1회 투약량
    private String resDailyDosesNumber;     // 1일 투여횟수
    private String resTotalDosingdays;      // 총 투약일수
    private String resPrescribeOrg;         // 처방기관
    private String resManufactureDate;      // 조제일자 YYYYMMDD  ← 추가
    private String resPrescribeNo;          // 처방전 교부번호    ← 추가
    private String resDrugCode;             // 약품코드          ← 추가
    private String imageUrl;                // 약 이미지 URL (식약처, 없으면 null) ← 신규
}