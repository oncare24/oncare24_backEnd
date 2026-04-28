package com.oncare.oncare24.hospital.service;

import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.DepartmentResult;
import com.oncare.oncare24.hospital.dto.Urgency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * LLM 실패 시 사용하는 키워드 매칭 폴백.
 * <p>
 * 정교한 NLP가 아니라 단순 키워드 포함 검사. LLM 호출이 일시적으로 실패해도 서비스가 계속 동작하게 하기 위함.
 * <p>
 * 응급 키워드는 보수적으로 잡음 (false positive를 false negative보다 선호 - 안전 우선).
 */
@Slf4j
@Service
public class KeywordFallbackService {

    /**
     * 응급도 HIGH로 판단할 키워드. 하나라도 매치되면 HIGH 분류.
     * <p>
     * 보수적으로 짧고 명확한 단어만 사용. "통증" 같은 일반어는 제외.
     */
    private static final List<String> EMERGENCY_KEYWORDS = List.of(
            "의식", "쓰러", "실신", "마비", "경련", "발작",
            "호흡곤란", "숨쉬기", "숨을 못", "숨이 안", "질식",
            "심한 출혈", "피가 멈추지", "토혈", "각혈",
            "심한 흉통", "가슴이 찢어", "가슴을 쥐어",
            "뇌졸중", "심근경색", "심정지", "응급",
            "자살", "스스로 해", "약을 먹"
    );

    /**
     * 진료과별 대표 키워드. 위에서부터 검사하므로 우선순위가 높은 진료과를 위에 둠.
     */
    private static final Map<Department, List<String>> DEPARTMENT_KEYWORDS = Map.ofEntries(
            Map.entry(Department.CARDIOLOGY,        List.of("심장", "가슴이 두근", "부정맥", "흉통")),
            Map.entry(Department.NEUROLOGY,         List.of("두통", "어지럼", "어지러", "마비", "감각이 없", "떨림")),
            Map.entry(Department.ORTHOPEDICS,       List.of("허리", "무릎", "관절", "골절", "삐었", "근육통", "어깨 통증")),
            Map.entry(Department.OPHTHALMOLOGY,     List.of("눈", "시력", "시야", "안구")),
            Map.entry(Department.OTOLARYNGOLOGY,    List.of("귀", "코", "목", "인후", "이명", "어지럼증")),
            Map.entry(Department.DERMATOLOGY,       List.of("피부", "발진", "두드러기", "여드름", "가려움", "두피")),
            Map.entry(Department.UROLOGY,           List.of("소변", "배뇨", "전립선", "방광")),
            Map.entry(Department.OBSTETRICS,        List.of("생리", "임신", "산부인", "월경", "자궁")),
            Map.entry(Department.PEDIATRICS,        List.of("아이", "유아", "아기", "소아")),
            Map.entry(Department.PSYCHIATRY,        List.of("우울", "불안", "공황", "잠이 안", "불면")),
            Map.entry(Department.DENTISTRY,         List.of("이가", "치아", "잇몸", "충치", "사랑니")),
            Map.entry(Department.INTERNAL_MEDICINE, List.of("배가", "복통", "설사", "변비", "소화", "속쓰림", "위", "장")),
            Map.entry(Department.FAMILY_MEDICINE,   List.of("감기", "열", "기침", "콧물", "오한", "몸살"))
    );

    /**
     * 키워드 매칭으로 진료과/응급도 결정.
     * 어느 진료과에도 매치되지 않으면 가정의학과로 폴백.
     */
    public DepartmentResult fallback(String symptoms) {
        String lower = symptoms.toLowerCase();
        Urgency urgency = detectUrgency(lower);
        Department department = detectDepartment(lower);

        String reason = "키워드 분석 결과 " + department.getKorean() + " 진료가 적합해 보입니다.";
        log.info("[Fallback] dept={}, urgency={}", department, urgency);
        return new DepartmentResult(department, urgency, reason, false);
    }

    private Urgency detectUrgency(String text) {
        for (String keyword : EMERGENCY_KEYWORDS) {
            if (text.contains(keyword)) {
                return Urgency.HIGH;
            }
        }
        return Urgency.MEDIUM;
    }

    private Department detectDepartment(String text) {
        for (Map.Entry<Department, List<String>> entry : DEPARTMENT_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return Department.FAMILY_MEDICINE; // 매칭 실패 시 가정의학과 (다양한 1차 진료 가능)
    }
}
