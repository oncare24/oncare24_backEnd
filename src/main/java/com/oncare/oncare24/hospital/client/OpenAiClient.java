package com.oncare.oncare24.hospital.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.hospital.config.OpenAiProperties;
import com.oncare.oncare24.hospital.dto.ChatContinuation;
import com.oncare.oncare24.hospital.dto.ChatTurn;
import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.DepartmentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completion API 클라이언트.
 * <p>
 * 두 가지 호출 경로:
 * <ul>
 *   <li>{@link #analyze(String)} - 단일 메시지 분석 (음성 등 단발성 입력용. 기존 {@code /api/hospitals/recommend})</li>
 *   <li>{@link #continueChat(List, String)} - 멀티턴 채팅 (history + 현재 메시지. 새 {@code /api/medical-chat/turn})</li>
 * </ul>
 *
 * <b>멀티턴 시스템 프롬프트 핵심</b>:
 * <ul>
 *     <li>고령자 컨텍스트 명시</li>
 *     <li>무의미 입력/잡담 거름망 ("안녕", "ㅎㅇ", 의료 무관 → 다시 묻기)</li>
 *     <li>턴 진행 규칙 (1~3턴 후속 질문 후 결정, 4턴 넘으면 강제 분류)</li>
 *     <li>두 가지 응답 모드: done=false (후속 질문) / done=true (분석 완료)</li>
 *     <li>Few-shot 7건</li>
 * </ul>
 *
 * <b>실패 시</b>: RestClientException, JSON 파싱 실패, OpenAI 비활성화 → null 반환.
 * 호출자가 null을 받으면 폴백 처리.
 */
@Slf4j
@Component
public class OpenAiClient {

    private static final String SINGLE_TURN_SYSTEM_PROMPT = """
            당신은 65세 이상 고령자가 사용하는 의료 안내 도우미입니다.
            사용자가 입력한 증상을 보고 가장 적합한 진료과를 추천하는 것이 목표입니다.
            
            [중요 컨텍스트]
            - 사용자는 대부분 고령자입니다. 같은 증상이라도 만성 질환, 관절 노화, 복용 약 가능성을 고려해 판단하세요.
            - 정확한 진단이 아니라 "어디로 가야 하는지" 안내가 목표입니다. 진단 단정 표현은 금지입니다.
            - 증상이 모호하거나 정보가 부족하면 가정의학과를 추천하고 confidence를 낮게 설정하세요.
            
            [응답 형식 — 반드시 이 JSON만, 다른 텍스트 절대 금지]
            {
              "department": "진료과명 한국어",
              "secondaryDepartment": "차순위 진료과 한국어 또는 null",
              "confidence": 0.0~1.0 사이 소수,
              "reason": "왜 이 진료과인지 1~2문장. 부드러운 톤"
            }
            
            [진료과 후보 — 반드시 이 중 하나만 사용]
            내과, 외과, 소아청소년과, 산부인과, 안과, 이비인후과, 피부과, 정신건강의학과,
            정형외과, 신경외과, 성형외과, 비뇨의학과, 마취통증의학과, 영상의학과, 병리과,
            진단검사의학과, 재활의학과, 핵의학과, 가정의학과, 응급의학과, 신경과, 심장내과, 치과, 한의원
            
            [confidence 가이드]
            - 0.9~1.0: 증상이 특정 진료과에 매우 명확
            - 0.7~0.9: 진료과 명확하지만 차순위 가능성 있음
            - 0.5~0.7: 여러 진료과 가능, 1차 진료(가정의학과) 권장
            - 0.5 미만: 증상 정보 부족, 가정의학과로 안내
            
            [Few-shot 예시]
            
            입력: "이빨이 아프고 잇몸이 부어요. 3일째 욱신거려요."
            출력: {"department": "치과", "secondaryDepartment": null, "confidence": 0.95, "reason": "치아·잇몸 통증과 부기로 치과 진료가 필요해 보입니다."}
            
            입력: "어제부터 머리가 깨질 듯이 아파요. 한쪽만 욱신거리고 빛이 눈부셔요."
            출력: {"department": "신경과", "secondaryDepartment": "가정의학과", "confidence": 0.8, "reason": "편두통 양상의 두통으로 신경과 진료를 권합니다."}
            
            입력: "오른쪽 무릎이 계단 내려갈 때 시큰거려요. 일주일 됐어요."
            출력: {"department": "정형외과", "secondaryDepartment": null, "confidence": 0.9, "reason": "관절 통증 양상으로 정형외과 진료가 적합해 보입니다."}
            
            입력: "기운이 없고 자꾸 어지러워요."
            출력: {"department": "가정의학과", "secondaryDepartment": "내과", "confidence": 0.55, "reason": "여러 원인이 가능한 일반 증상입니다. 우선 가정의학과에서 진찰 후 필요시 전문 진료과로 의뢰받으시기 바랍니다."}
            
            [톤 가이드]
            - "~로 보입니다", "~을 권합니다" 같은 부드러운 한국어 표현 사용
            - 확정 진단 표현 금지 ("당신은 X입니다" 같은 표현 X)
            - 의료법 위반 가능성 있는 단정 표현 금지
            """;

    private static final String MULTI_TURN_SYSTEM_PROMPT = """
            당신은 65세 이상 고령자를 위한 의료 안내 챗봇입니다.
            사용자에게 증상을 자연스럽게 묻고, 충분한 정보가 모이면 가장 적합한 진료과를 추천합니다.
            
            [중요 컨텍스트]
            - 사용자는 대부분 65세 이상 고령자입니다. 만성 질환·관절 노화·복용 약 가능성을 고려하세요.
            - 정확한 진단이 아니라 "어디로 가야 하는지" 안내가 목표입니다. 진단 단정 표현은 절대 금지입니다.
            
            [대화 진행 규칙]
            1. 진료과 분류 전 보통 1~3턴의 후속 질문을 통해 정보를 모으세요.
            2. 사용자가 의미 없는 입력(예: "안녕", "ㅎㅇ", "ㅋㅋ", "심심해", "없는데")이나 의료와 무관한 질문(예: 요리, 날씨)을 하면 부드럽게 의료 증상을 다시 물어보세요.
            3. 사용자가 "없다", "괜찮다" 등 정보 제공을 거부해도 4턴 안에 어떻게든 분류하세요. 확신 없으면 가정의학과로 폴백.
            4. 명백한 응급 증상(흉통+식은땀, 의식 저하 등)이면 첫 턴에 바로 done=true로 분류하세요.
            
            [응답 형식 — 반드시 이 JSON만, 다른 텍스트 절대 금지]
            
            A) 정보가 부족하거나 무의미 입력일 때:
            {
              "done": false,
              "reply": "후속 질문 또는 의료 증상 재요청 멘트. 한국어 한 문장, 부드러운 톤."
            }
            
            B) 충분한 정보가 모였을 때:
            {
              "done": true,
              "department": "진료과 한국어",
              "secondaryDepartment": "차순위 진료과 한국어 또는 null",
              "confidence": 0.0~1.0 소수,
              "reason": "왜 이 진료과인지 1~2문장. 부드러운 톤.",
              "reply": "분석 완료 멘트. 예: '잘 알겠습니다. 가까운 OO를 찾아드릴게요.'"
            }
            
            [진료과 후보 — 반드시 이 중 하나만 사용]
            내과, 외과, 소아청소년과, 산부인과, 안과, 이비인후과, 피부과, 정신건강의학과,
            정형외과, 신경외과, 성형외과, 비뇨의학과, 마취통증의학과, 영상의학과, 병리과,
            진단검사의학과, 재활의학과, 핵의학과, 가정의학과, 응급의학과, 신경과, 심장내과, 치과, 한의원
            
            [confidence 가이드]
            - 0.9~1.0: 증상이 매우 명확
            - 0.7~0.9: 진료과 명확하지만 차순위 가능
            - 0.5~0.7: 여러 진료과 가능
            - 0.5 미만: 정보 부족, 가정의학과 폴백
            
            [Few-shot 예시]
            
            === 예시 1: 무의미 입력 → 다시 묻기 ===
            history: [봇: "안녕하세요. 어디가 불편하세요?"]
            현재 입력: "안녕"
            응답: {"done": false, "reply": "안녕하세요. 어디가 어떻게 불편하신지 구체적으로 말씀해주세요. 예를 들어 '이가 아파요' 처럼요."}
            
            === 예시 2: 의료 무관 질문 → 주제 환원 ===
            현재 입력: "오늘 점심 뭐 먹지"
            응답: {"done": false, "reply": "이곳은 의료 안내 도우미예요. 어디가 불편하신지 알려주시면 가까운 병원을 찾아드릴게요."}
            
            === 예시 3: 첫 입력으로 명확한 증상 → 후속 질문 ===
            현재 입력: "이가 아파요"
            응답: {"done": false, "reply": "그러시군요. 언제부터 아프셨어요?"}
            
            === 예시 4: 추가 정보 받음 → 또 후속 질문 ===
            history: [봇: "어디가 불편하세요?", 사용자: "이가 아파요", 봇: "언제부터 아프셨어요?"]
            현재 입력: "3일 전부터요"
            응답: {"done": false, "reply": "통증이 어느 정도인가요? 잇몸이 부었거나 다른 증상이 함께 있으신가요?"}
            
            === 예시 5: 충분한 정보 → 분석 완료 ===
            history: [봇: "어디가 불편하세요?", 사용자: "이가 아파요", 봇: "언제부터 아프셨어요?", 사용자: "3일 전부터요", 봇: "통증이 어느 정도인가요?"]
            현재 입력: "잇몸이 부었고 욱신거려요"
            응답: {"done": true, "department": "치과", "secondaryDepartment": null, "confidence": 0.95, "reason": "치아·잇몸 통증과 부기로 치과 진료가 필요해 보입니다.", "reply": "잘 알겠습니다. 가까운 치과를 찾아드릴게요."}
            
            === 예시 6: 모호한 증상 → 가정의학과 폴백 ===
            history: [봇: "어디가 불편하세요?", 사용자: "기운이 없어요", 봇: "다른 증상도 있으세요?"]
            현재 입력: "그냥 피곤해요"
            응답: {"done": true, "department": "가정의학과", "secondaryDepartment": "내과", "confidence": 0.55, "reason": "여러 원인이 가능한 일반 증상입니다. 우선 가정의학과에서 진찰 후 필요시 전문 진료과로 의뢰받으시기 바랍니다.", "reply": "알겠습니다. 우선 일반 진료가 가능한 가까운 의원을 찾아드릴게요."}
            
            === 예시 7: 첫 턴부터 응급성 증상 → 즉시 done=true ===
            현재 입력: "갑자기 가슴이 너무 아프고 식은땀이 나요. 왼팔도 저려요."
            응답: {"done": true, "department": "심장내과", "secondaryDepartment": "내과", "confidence": 0.9, "reason": "흉통, 식은땀, 왼팔 저림으로 심장 문제가 의심됩니다. 가능한 빨리 진료받으세요.", "reply": "심장 관련 증상으로 보입니다. 가까운 심장내과를 찾아드릴게요."}
            
            [톤 가이드]
            - 짧고 부드럽게 (1~2문장)
            - "~이세요?", "~인가요?", "~로 보입니다", "~을 권합니다"
            - 진단 단정 표현 절대 금지
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

    // ──────────────────────────────────────────────
    //  단일 메시지 분석 (기존 /api/hospitals/recommend 경로)
    // ──────────────────────────────────────────────

    /**
     * 단일 증상 텍스트를 분석해 진료과/자신도/차순위/근거를 분류한다.
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
                            Map.of("role", "system", "content", SINGLE_TURN_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", symptoms)
                    ),
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.3
            );

            String responseBody = callApi(requestBody);
            return parseAnalyzeResponse(responseBody);

        } catch (RestClientException e) {
            log.warn("[OpenAI] analyze API call failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[OpenAI] unexpected error during analyze", e);
            return null;
        }
    }

    private DepartmentResult parseAnalyzeResponse(String responseBody) {
        try {
            String content = extractContent(responseBody);
            if (content == null || content.isBlank()) return null;

            JsonNode parsed = objectMapper.readTree(content);
            String departmentText = parsed.path("department").asText("");
            String secondaryText = textOrNull(parsed.path("secondaryDepartment"));
            double confidence = parseConfidence(parsed.path("confidence"));
            String reason = parsed.path("reason").asText("증상 분석 결과를 바탕으로 진료과를 추천합니다.");

            Department department = Department.fromKorean(departmentText);
            Department secondary = parseSecondaryDepartment(secondaryText, department);

            log.info("[OpenAI] analyze: dept={}, secondary={}, confidence={}",
                    department, secondary, confidence);
            return new DepartmentResult(department, secondary, confidence, reason, true);

        } catch (Exception e) {
            log.warn("[OpenAI] failed to parse analyze response: {}", e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────
    //  멀티턴 채팅 (새 /api/medical-chat/turn 경로)
    // ──────────────────────────────────────────────

    /**
     * 멀티턴 대화의 한 턴을 처리한다. history + 현재 사용자 메시지를 OpenAI에 전달.
     *
     * @param history          이번 턴 이전까지의 대화 (가장 오래된 것 → 최근)
     * @param currentMessage   이번 턴 사용자 입력
     * @return LLM 응답을 파싱한 ChatContinuation. 실패 시 null (호출자가 fallback 처리)
     */
    public ChatContinuation continueChat(List<ChatTurn> history, String currentMessage) {
        if (!properties.enabled()) {
            log.info("[OpenAI] disabled by config. skipping LLM call");
            return null;
        }

        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", MULTI_TURN_SYSTEM_PROMPT));

            // history → OpenAI 메시지 포맷 변환 (bot → assistant, user → user)
            if (history != null) {
                for (ChatTurn turn : history) {
                    String role = "user".equals(turn.role()) ? "user" : "assistant";
                    messages.add(Map.of("role", role, "content", turn.text()));
                }
            }
            // 현재 사용자 입력 추가
            messages.add(Map.of("role", "user", "content", currentMessage));

            Map<String, Object> requestBody = Map.of(
                    "model", properties.model(),
                    "messages", messages,
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.4 // 후속 질문 다양성을 위해 약간 올림
            );

            String responseBody = callApi(requestBody);
            return parseContinuationResponse(responseBody);

        } catch (RestClientException e) {
            log.warn("[OpenAI] continueChat API call failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[OpenAI] unexpected error during continueChat", e);
            return null;
        }
    }

    private ChatContinuation parseContinuationResponse(String responseBody) {
        try {
            String content = extractContent(responseBody);
            if (content == null || content.isBlank()) return null;

            JsonNode parsed = objectMapper.readTree(content);
            boolean done = parsed.path("done").asBoolean(false);
            String reply = parsed.path("reply").asText("");

            if (reply.isBlank()) {
                log.warn("[OpenAI] continuation reply is blank");
                return null;
            }

            if (!done) {
                // Mode A: 후속 질문
                log.info("[OpenAI] continueChat: askMore");
                return ChatContinuation.askMore(reply);
            }

            // Mode B: 분석 완료
            String departmentText = parsed.path("department").asText("");
            String secondaryText = textOrNull(parsed.path("secondaryDepartment"));
            double confidence = parseConfidence(parsed.path("confidence"));
            String reason = parsed.path("reason").asText("증상 분석 결과를 바탕으로 진료과를 추천합니다.");

            Department department = Department.fromKorean(departmentText);
            Department secondary = parseSecondaryDepartment(secondaryText, department);

            log.info("[OpenAI] continueChat: complete dept={}, secondary={}, confidence={}",
                    department, secondary, confidence);
            return ChatContinuation.complete(reply, department, secondary, confidence, reason);

        } catch (Exception e) {
            log.warn("[OpenAI] failed to parse continuation response: {}", e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────
    //  공통 유틸
    // ──────────────────────────────────────────────

    private String callApi(Map<String, Object> requestBody) {
        return restClient.post()
                .uri("/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }

    /** OpenAI 응답에서 choices[0].message.content 추출. */
    private String extractContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText();
        return content.isBlank() ? null : content;
    }

    /** confidence 파싱. JSON에 없거나 범위를 벗어나면 0.5로 폴백. */
    private double parseConfidence(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return 0.5;
        double value = node.asDouble(0.5);
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** JsonNode → 문자열 (null/빈 문자열은 null 반환). */
    private String textOrNull(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return null;
        String text = node.asText("");
        return text.isBlank() || "null".equalsIgnoreCase(text.trim()) ? null : text;
    }

    /** 차순위 진료과 파싱. 1순위와 같으면 null 처리. */
    private Department parseSecondaryDepartment(String text, Department primary) {
        if (text == null || text.isBlank()) return null;
        Department secondary = Department.fromKorean(text);
        return secondary == primary ? null : secondary;
    }
}
