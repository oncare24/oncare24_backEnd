package com.oncare.oncare24.hospital.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.List;

/**
 * 멀티턴 채팅 한 턴 요청.
 * <p>
 * 클라이언트는 매 턴마다 이 API를 호출한다. 서버는 history + currentMessage를 OpenAI에 전달하여
 * LLM이 후속 질문을 생성하거나 진료과 분석을 완료한다.
 *
 * <p><b>대화 상태는 stateless</b>: 서버가 세션을 저장하지 않고 클라이언트가 매번 history 전체를
 * 보낸다. 단순한 디자인 + Redis 의존성 추가 안 함.
 *
 * @param sessionId      클라이언트 생성 세션 ID (로깅용. 서버 식별 안 함)
 * @param message        이번 턴의 사용자 입력 (1~500자, 필수)
 * @param history        이번 턴 이전까지의 대화 기록 (가장 오래된 것 → 최근. 최대 20개)
 * @param latitude       위치 위도 (선택. 없으면 백엔드 폴백 체인 동작)
 * @param longitude      위치 경도 (선택)
 * @param radius         검색 반경(미터). 기본 5000, 1000~20000 사이.
 */
public record MedicalChatRequest(

        @NotBlank(message = "sessionId는 필수입니다.")
        String sessionId,

        @NotBlank(message = "message는 필수입니다.")
        @Size(min = 1, max = 500, message = "메시지는 1자 이상 500자 이하로 입력해주세요.")
        String message,

        @Size(max = 20, message = "history는 20개 이하만 허용됩니다.")
        @Valid
        List<ChatTurn> history,

        @DecimalMin(value = "33.0", message = "위도가 한국 영토 범위를 벗어납니다.")
        @DecimalMax(value = "39.0", message = "위도가 한국 영토 범위를 벗어납니다.")
        Double latitude,

        @DecimalMin(value = "124.0", message = "경도가 한국 영토 범위를 벗어납니다.")
        @DecimalMax(value = "132.0", message = "경도가 한국 영토 범위를 벗어납니다.")
        Double longitude,

        Integer radius
) {

    public static final int DEFAULT_RADIUS_METERS = 5000;
    public static final int MIN_RADIUS_METERS = 1000;
    public static final int MAX_RADIUS_METERS = 20000;

    public int radiusOrDefault() {
        if (radius == null) return DEFAULT_RADIUS_METERS;
        return Math.max(MIN_RADIUS_METERS, Math.min(MAX_RADIUS_METERS, radius));
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    /** null-safe history 접근. */
    public List<ChatTurn> safeHistory() {
        return history == null ? Collections.emptyList() : history;
    }

    /** history에서 user 발화 개수 (현재 메시지 제외). */
    public int userTurnCountInHistory() {
        return (int) safeHistory().stream()
                .filter(t -> "user".equals(t.role()))
                .count();
    }
}
