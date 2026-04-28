package com.oncare.oncare24.hospital.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.hospital.config.OpenAiProperties;
import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.DepartmentResult;
import com.oncare.oncare24.hospital.dto.Urgency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completion API 클라이언트.
 * <p>
 * 시스템 프롬프트로 LLM에게 "JSON으로만 답하라"고 강제하고, 유저 프롬프트로 증상을 전달.
 * 응답을 파싱해서 {@link DepartmentResult} 형태로 반환.
 *
 * <b>실패 시</b>: RestClientException, JSON 파싱 실패, OpenAI 비활성화 → null 반환.
 * 호출자가 null을 받으면 KeywordFallbackService로 폴백.
 *
 * <b>비용 메모</b>: gpt-4o-mini 기준 1회 호출 약 $0.0001 (사실상 무료에 가까움).
 */
@Slf4j
@Component
public class OpenAiClient {

    private static final String SYSTEM_PROMPT = """
            당신은 친절한 의료 상담 도우미입니다. 사용자가 입력한 증상을 보고 가장 적합한 진료과와 응급도를 분류합니다.
            
            다음 JSON 형식으로만 답하세요. 다른 설명이나 마크다운은 절대 포함하지 마세요.
            
            {
              "department": "진료과명 한국어",
              "urgency": "LOW | MEDIUM | HIGH",
              "reason": "왜 이 진료과/응급도로 분류했는지 1~2문장 설명"
            }
            
            진료과 후보 (반드시 이 중 하나):
            내과, 외과, 소아청소년과, 산부인과, 안과, 이비인후과, 피부과, 정신건강의학과,
            정형외과, 신경외과, 성형외과, 비뇨의학과, 마취통증의학과, 영상의학과, 병리과,
            진단검사의학과, 재활의학과, 핵의학과, 가정의학과, 응급의학과, 신경과, 심장내과, 치과, 한의원
            
            응급도 기준:
            - HIGH: 의식 저하, 심한 흉통, 호흡곤란, 심한 출혈, 마비, 뇌졸중 의심, 심정지 의심 등 즉시 응급실 필요
            - MEDIUM: 고열 지속, 심한 통증, 외상, 24시간 내 진료 권장
            - LOW: 가벼운 증상, 만성 관리, 정기 진료
            
            reason은 사용자에게 보여줄 친절한 한국어 설명. "~로 보입니다", "~이 의심됩니다" 같은 부드러운 표현 사용.
            확정 진단이 아니라 진료과 추천임을 암시하는 톤. 의료법 위반 가능성 있는 진단 단정 표현 금지.
            """;

    private final OpenAiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiClient(
            OpenAiProperties properties,
            @Qualifier("openAiRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 증상을 분석해 진료과/응급도를 분류한다.
     *
     * @return 분석 성공 시 DepartmentResult, 실패 시 null (호출자가 fallback 처리)
     */
    public DepartmentResult analyze(String symptoms) {
        if (!properties.enabled()) {
            log.info("[OpenAI] disabled by config. skipping LLM call");
            return null;
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", properties.model(),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", symptoms)
                    ),
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.3
            );

            String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseResponse(responseBody);

        } catch (RestClientException e) {
            log.warn("[OpenAI] API call failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[OpenAI] unexpected error during analyze", e);
            return null;
        }
    }

    /**
     * OpenAI 응답에서 message.content 추출 → JSON 파싱 → DepartmentResult 생성.
     */
    private DepartmentResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content.isBlank()) {
                log.warn("[OpenAI] empty content in response");
                return null;
            }

            JsonNode parsed = objectMapper.readTree(content);
            String departmentText = parsed.path("department").asText("");
            String urgencyText = parsed.path("urgency").asText("MEDIUM");
            String reason = parsed.path("reason").asText("증상 분석 결과를 바탕으로 진료과를 추천합니다.");

            Department department = Department.fromKorean(departmentText);
            Urgency urgency = parseUrgency(urgencyText);

            log.info("[OpenAI] analyzed: dept={}, urgency={}", department, urgency);
            return new DepartmentResult(department, urgency, reason, true);

        } catch (Exception e) {
            log.warn("[OpenAI] failed to parse response: {}", e.getMessage());
            return null;
        }
    }

    private Urgency parseUrgency(String text) {
        try {
            return Urgency.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Urgency.MEDIUM;
        }
    }
}
