package com.oncare.oncare24.medication.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodefKeyHasherTest {

    private final CodefKeyHasher hasher = new CodefKeyHasher("test-secret");

    @Test
    void hash_returnsNull_whenPrescribeNoOrDrugCodeMissing() {
        assertThat(hasher.hash(null, "D1")).isNull();
        assertThat(hasher.hash("", "D1")).isNull();
        assertThat(hasher.hash("R1", null)).isNull();
        assertThat(hasher.hash("R1", " ")).isNull();
    }

    @Test
    void hash_isStableAndHex64_forSameInput() {
        String a = hasher.hash("R1", "D1");
        String b = hasher.hash("R1", "D1");
        assertThat(a).isNotNull().hasSize(64).isEqualTo(b);
        assertThat(hasher.hash("R1", "D2")).isNotEqualTo(a);
    }

    @Test
    void hashFallback_isStable_neverNull_andDiffersByDrug() {
        String a = hasher.hashFallback("타이레놀", "20260601", "3");
        String b = hasher.hashFallback("타이레놀", "20260601", "3");
        // 재분석 시 동일 입력이면 같은 키 → 중복 차단 근거
        assertThat(a).isNotNull().hasSize(64).isEqualTo(b);
        // 다른 약이면 다른 키
        assertThat(hasher.hashFallback("게보린", "20260601", "3")).isNotEqualTo(a);
        // null 입력에도 예외 없이 키 생성
        assertThat(hasher.hashFallback(null, null, null)).isNotNull().hasSize(64);
    }
}
