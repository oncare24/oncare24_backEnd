package com.oncare.oncare24.hospital.service;

import com.oncare.oncare24.hospital.client.OpenAiClient;
import com.oncare.oncare24.hospital.dto.ChatContinuation;
import com.oncare.oncare24.hospital.dto.ChatTurn;
import com.oncare.oncare24.hospital.dto.Department;
import com.oncare.oncare24.hospital.dto.DepartmentResult;
import com.oncare.oncare24.hospital.dto.HospitalInfo;
import com.oncare.oncare24.hospital.dto.MedicalChatRequest;
import com.oncare.oncare24.hospital.dto.MedicalChatResponse;
import com.oncare.oncare24.hospital.dto.RecommendRequest;
import com.oncare.oncare24.hospital.dto.RecommendResponse;
import com.oncare.oncare24.hospital.dto.ScoredHospital;
import com.oncare.oncare24.hospital.util.DepartmentNameMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/**
 * 멀티턴 채팅 한 턴을 처리하는 오케스트레이터.
 *
 * <pre>
 * 1. LLM 호출 (OpenAiClient.continueChat)
 *    - 실패 시 폴백 (history.userTurnCount 기반 분기)
 * 2. LLM이 done=false → 후속 질문 응답 반환
 * 3. LLM이 done=true → 위치 결정 + 병원 검색 + 스코어링/필터링 + 응답 조립
 *    - MATCH 0개 시 자동 반경 확장 (5km → 15km)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalChatService {

    /** 최대 user 발화 수. 이를 넘으면 강제 done=true (LLM 폭주 방지). */
    private static final int MAX_USER_TURNS = 5;

    /** 응답에 포함할 최대 병원 개수. */
    private static final int MAX_HOSPITALS_IN_RESPONSE = 10;

    /** 자동 반경 확장 시 사용할 반경(미터). */
    private static final int EXPANDED_RADIUS_METERS = 15000;

    /** LLM 실패 시 폴백 후속 질문. */
    private static final List<String> FALLBACK_FOLLOWUPS = List.of(
            "그러시군요. 언제부터 그런 증상이 있으셨어요?",
            "통증은 어느 정도이신가요? 함께 다른 증상이 있다면 알려주세요."
    );

    private static final String FALLBACK_COMPLETE_REPLY =
            "잘 알겠습니다. 가까운 병원을 찾아드리고 있어요. 잠시만 기다려주세요.";

    private final OpenAiClient openAiClient;
    private final KeywordFallbackService keywordFallbackService;
    private final UserLocationResolver locationResolver;
    private final HospitalSearchService hospitalSearchService;
    private final HospitalScoringService scoringService;

    @Transactional(readOnly = true)
    public MedicalChatResponse processTurn(Long userId, MedicalChatRequest req) {
        int userTurnsBefore = req.userTurnCountInHistory();
        log.info("[MedicalChat] userId={}, sessionId={}, prevUserTurns={}, msgLen={}",
                userId, req.sessionId(), userTurnsBefore, req.message().length());

        // 1. LLM 호출
        ChatContinuation cont = openAiClient.continueChat(req.safeHistory(), req.message());

        // 2. LLM 실패 시 폴백
        if (cont == null) {
            cont = fallback(req, userTurnsBefore);
        }

        // 3. 최대 턴 초과 시 강제 done=true
        if (!cont.done() && userTurnsBefore + 1 >= MAX_USER_TURNS) {
            log.info("[MedicalChat] force done due to max turns (userTurns={})", userTurnsBefore + 1);
            cont = forceCompleteFromHistory(req);
        }

        // 4. 분기: 후속 질문 vs 분석 완료
        if (!cont.done()) {
            return MedicalChatResponse.askMore(req.sessionId(), cont.reply());
        }

        // 5. 분석 완료 → 병원 검색 + 응답 조립
        RecommendResponse result = buildRecommendResponse(userId, req, cont);
        return MedicalChatResponse.complete(req.sessionId(), cont.reply(), result);
    }

    private ChatContinuation fallback(MedicalChatRequest req, int userTurnsBefore) {
        if (userTurnsBefore < FALLBACK_FOLLOWUPS.size()) {
            String reply = FALLBACK_FOLLOWUPS.get(userTurnsBefore);
            log.info("[MedicalChat] fallback askMore (idx={})", userTurnsBefore);
            return ChatContinuation.fallbackAskMore(reply);
        }
        return forceCompleteFromHistory(req);
    }

    private ChatContinuation forceCompleteFromHistory(MedicalChatRequest req) {
        String combined = combineUserMessages(req);
        DepartmentResult kw = keywordFallbackService.fallback(combined);
        log.info("[MedicalChat] keyword fallback: dept={}, confidence={}",
                kw.department(), kw.confidence());
        return ChatContinuation.fallbackComplete(
                FALLBACK_COMPLETE_REPLY, kw.department(), kw.confidence(), kw.reason());
    }

    private String combineUserMessages(MedicalChatRequest req) {
        StringBuilder sb = new StringBuilder();
        for (ChatTurn t : req.safeHistory()) {
            if ("user".equals(t.role())) {
                if (sb.length() > 0) sb.append(". ");
                sb.append(t.text().trim());
            }
        }
        if (sb.length() > 0) sb.append(". ");
        sb.append(req.message().trim());
        return sb.toString();
    }

    /**
     * 분석 완료 시 위치 결정 + 병원 검색 + 자동 확장 + 응답 조립.
     */
    private RecommendResponse buildRecommendResponse(
            Long userId, MedicalChatRequest req, ChatContinuation cont) {

        RecommendRequest recommendReq = new RecommendRequest(
                req.message(), req.latitude(), req.longitude(), req.radius());

        UserLocationResolver.ResolvedLocation loc =
                locationResolver.resolve(userId, recommendReq);

        // 1차 검색 (요청 반경)
        int requestedRadius = recommendReq.radiusOrDefault();
        List<ScoredHospital> scored = searchAndScore(loc, requestedRadius, cont.department());

        // 자동 반경 확장: MATCH 0개 + 확장 가능 조건
        if (shouldExpandRadius(scored, requestedRadius, cont.department())) {
            log.info("[MedicalChat] auto-expanding radius {}m → {}m (no exact match found)",
                    requestedRadius, EXPANDED_RADIUS_METERS);
            scored = searchAndScore(loc, EXPANDED_RADIUS_METERS, cont.department());
        }

        List<ScoredHospital> top = scored.stream().limit(MAX_HOSPITALS_IN_RESPONSE).toList();

        return new RecommendResponse(
                cont.department().getKorean(),
                cont.department().getCode(),
                cont.secondaryDepartment() != null ? cont.secondaryDepartment().getKorean() : null,
                cont.confidence(),
                cont.reason(),
                top,
                loc.latitude(),
                loc.longitude(),
                loc.source()
        );
    }

    /**
     * 검색 + 스코어링 + 필터링을 한 번에 수행. 자동 확장 전후로 동일 로직 사용.
     */
    private List<ScoredHospital> searchAndScore(
            UserLocationResolver.ResolvedLocation loc, int radius, Department department) {

        List<HospitalInfo> hospitals = hospitalSearchService.searchHospitals(
                loc.latitude(), loc.longitude(), radius, department);

        return scoringService.scoreAndFilter(
                hospitals, loc.latitude(), loc.longitude(), LocalTime.now(), department);
    }

    /**
     * 자동 반경 확장 조건:
     * <ul>
     *     <li>진료과가 이름 매칭 가능 (정형외과/치과/안과 등)</li>
     *     <li>현재 반경이 확장 반경보다 작음</li>
     *     <li>현재 결과에 MATCH가 0개</li>
     * </ul>
     */
    private boolean shouldExpandRadius(
            List<ScoredHospital> currentResult, int currentRadius, Department department) {
        if (department == null || !DepartmentNameMatcher.isFilterable(department)) {
            return false;
        }
        if (currentRadius >= EXPANDED_RADIUS_METERS) {
            return false;
        }
        return HospitalScoringService.countExactMatch(currentResult, department) == 0;
    }
}
