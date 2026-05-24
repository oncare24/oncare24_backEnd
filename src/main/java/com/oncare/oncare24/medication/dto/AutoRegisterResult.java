// AutoRegisterResult.java
package com.oncare.oncare24.medication.dto;

import java.util.List;

/** 자동 등록 결과. 모두 약 이름 목록. */
public record AutoRegisterResult(
        List<String> registered,   // 등록됨
        List<String> skipped,      // 횟수·일수·조제일 없어서 직접 등록 안내
        List<String> duplicates    // 같은 처방-약 이미 있음
) {}