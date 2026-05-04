package com.oncare.oncare24.hospital.dto;

/**
 * 멀티턴 채팅 한 턴 응답.
 *
 * <p>두 가지 모드:
 * <ul>
 *   <li><b>done=false</b>: LLM이 정보를 더 받아야 함. {@code reply}는 후속 질문 또는
 *       무의미 입력 재요청 멘트. {@code result}는 null.</li>
 *   <li><b>done=true</b>: 분석 완료. {@code reply}는 마무리 멘트.
 *       {@code result}에 진료과 + 병원 리스트가 채워짐. 클라이언트는 결과 화면으로 이동해야 함.</li>
 * </ul>
 *
 * @param sessionId   클라이언트가 보낸 세션 ID 그대로 반환
 * @param done        대화 종료 여부
 * @param reply       봇이 화면에 표시할 메시지
 * @param result      done=true일 때만 채워짐 (병원 추천 결과)
 */
public record MedicalChatResponse(
        String sessionId,
        boolean done,
        String reply,
        RecommendResponse result
) {

    /** 후속 질문 응답 생성 (done=false). */
    public static MedicalChatResponse askMore(String sessionId, String reply) {
        return new MedicalChatResponse(sessionId, false, reply, null);
    }

    /** 분석 완료 응답 생성 (done=true). */
    public static MedicalChatResponse complete(String sessionId, String reply, RecommendResponse result) {
        return new MedicalChatResponse(sessionId, true, reply, result);
    }
}
