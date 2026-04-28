package com.oncare.oncare24.hospital.dto;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 진료과 코드 (국립중앙의료원 API의 dgsbjtCd 기준).
 * <p>
 * LLM이 자유 텍스트로 응답할 수 있어 우리 enum 이름과 다를 수 있다 → {@link #fromKorean(String)}로 매핑.
 * 매칭 실패 시 fallback은 {@link #INTERNAL_MEDICINE}(내과). 캡스톤 단계에선 충분.
 *
 * <p><b>code</b> 필드는 NMC API 호출 시 dgsbjtCd 파라미터로 사용. 두 자리 숫자.
 */
@Getter
@RequiredArgsConstructor
public enum Department {

    INTERNAL_MEDICINE("01", "내과"),
    SURGERY("02", "외과"),
    PEDIATRICS("03", "소아청소년과"),
    OBSTETRICS("04", "산부인과"),
    OPHTHALMOLOGY("05", "안과"),
    OTOLARYNGOLOGY("06", "이비인후과"),
    DERMATOLOGY("07", "피부과"),
    PSYCHIATRY("08", "정신건강의학과"),
    ORTHOPEDICS("09", "정형외과"),
    NEUROSURGERY("10", "신경외과"),
    PLASTIC_SURGERY("11", "성형외과"),
    UROLOGY("12", "비뇨의학과"),
    ANESTHESIOLOGY("13", "마취통증의학과"),
    RADIOLOGY("14", "영상의학과"),
    PATHOLOGY("15", "병리과"),
    LAB_MEDICINE("16", "진단검사의학과"),
    REHABILITATION("17", "재활의학과"),
    NUCLEAR_MEDICINE("18", "핵의학과"),
    FAMILY_MEDICINE("19", "가정의학과"),
    EMERGENCY_MEDICINE("20", "응급의학과"),
    NEUROLOGY("21", "신경과"),
    CARDIOLOGY("22", "심장내과"),
    DENTISTRY("23", "치과"),
    ORIENTAL_MEDICINE("24", "한의원"),
    OTHER("99", "기타");

    private final String code;
    private final String korean;

    /**
     * 한국어 진료과명으로부터 enum 변환.
     * <p>
     * 매칭 규칙: 정확 일치 → 부분 포함. 둘 다 실패 시 INTERNAL_MEDICINE 반환 (안전 폴백).
     * <p>
     * LLM이 "내과", "내과(소화기)", "소화기내과" 같이 변형해서 답할 수 있어 부분 매칭이 필요.
     */
    public static Department fromKorean(String korean) {
        if (korean == null || korean.isBlank()) {
            return INTERNAL_MEDICINE;
        }
        String trimmed = korean.trim();

        return Arrays.stream(values())
                .filter(d -> d.korean.equals(trimmed))
                .findFirst()
                .or(() -> Arrays.stream(values())
                        .filter(d -> trimmed.contains(d.korean) || d.korean.contains(trimmed))
                        .findFirst())
                .orElse(INTERNAL_MEDICINE);
    }

    /**
     * 코드(예: "01")로 enum 변환. NMC API 응답을 enum으로 매핑할 때 사용.
     */
    public static Department fromCode(String code) {
        return Arrays.stream(values())
                .filter(d -> d.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                        "Unknown department code: " + code));
    }
}
