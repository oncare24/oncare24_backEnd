// PrescriptionImportItem.java
package com.oncare.oncare24.medication.dto;

/** 자동 등록 입력. drugsafety.PrescriptionDto에서 필요한 값만 매핑해 넘긴다. */
public record PrescriptionImportItem(
        String drugName,
        String dailyDosesNumber,
        String totalDosingdays,
        String manufactureDate,
        String prescribeNo,
        String drugCode
) {}