package com.oncare.oncare24.hospital.service;

import com.oncare.oncare24.hospital.client.OpenAiClient;
import com.oncare.oncare24.hospital.dto.DepartmentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LLM 분석 + 키워드 폴백을 통합하는 파사드.
 * <p>
 * 호출자({@link HospitalRecommendService}) 입장에선 LLM 가용 여부를 신경 쓸 필요 없이
 * "증상 → DepartmentResult"라는 인터페이스만 알면 된다.
 * <p>
 * 동작:
 * <ol>
 *     <li>OpenAI 호출 시도</li>
 *     <li>실패 시 KeywordFallback 호출</li>
 * </ol>
 * 둘 중 하나는 반드시 성공 (KeywordFallback은 절대 실패하지 않음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAnalysisService {

    private final OpenAiClient openAiClient;
    private final KeywordFallbackService keywordFallbackService;

    public DepartmentResult analyze(String symptoms) {
        DepartmentResult llmResult = openAiClient.analyze(symptoms);
        if (llmResult != null) {
            return llmResult;
        }
        log.info("[LLM Analysis] falling back to keyword matching");
        return keywordFallbackService.fallback(symptoms);
    }
}
