package com.oncare.oncare24.hospital.dto;

/**
 * OpenAiClient.continueChat()의 내부 응답 — LLM 한 턴 결과.
 * <p>
 * 두 가지 케이스:
 * <ul>
 *   <li><b>done=false</b>: 후속 질문만 채워짐. department 등은 null.</li>
 *   <li><b>done=true</b>: 진료과 + confidence + reason + reply 모두 채워짐.</li>
 * </ul>
 *
 * <p>Service 계층에서 이 값을 보고 후속 질문 응답을 만들거나, 병원 검색 + 응답 조립을 진행한다.
 *
 * @param done                  분석 완료 여부 (false면 후속 질문)
 * @param reply                 사용자에게 보여줄 봇 메시지
 * @param department            done=true일 때 1순위 진료과 (else null)
 * @param secondaryDepartment   차순위 진료과 (없거나 done=false면 null)
 * @param confidence            done=true일 때 분류 자신도 (0.0~1.0, else 0.0)
 * @param reason                done=true일 때 추론 근거 (else null)
 * @param fromLlm               true면 LLM이 분석 / false면 키워드 폴백
 */
public record ChatContinuation(
        boolean done,
        String reply,
        Department department,
        Department secondaryDepartment,
        double confidence,
        String reason,
        boolean fromLlm
) {

    /** 후속 질문 케이스 (LLM 정상 응답). */
    public static ChatContinuation askMore(String reply) {
        return new ChatContinuation(false, reply, null, null, 0.0, null, true);
    }

    /** 분석 완료 케이스 (LLM 정상 응답). */
    public static ChatContinuation complete(
            String reply, Department dept, Department secondary, double confidence, String reason) {
        return new ChatContinuation(true, reply, dept, secondary, confidence, reason, true);
    }

    /** 키워드 폴백 케이스 (LLM 실패 시). */
    public static ChatContinuation fallbackComplete(
            String reply, Department dept, double confidence, String reason) {
        return new ChatContinuation(true, reply, dept, null, confidence, reason, false);
    }

    /** 키워드 폴백 후속 질문 케이스 (LLM 실패 + 아직 충분한 정보 없을 때). */
    public static ChatContinuation fallbackAskMore(String reply) {
        return new ChatContinuation(false, reply, null, null, 0.0, null, false);
    }
}
