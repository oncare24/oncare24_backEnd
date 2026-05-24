package com.oncare.oncare24.notification.sender;

import java.util.Collections;
import java.util.Map;

/**
 * FCM 푸시 발송 추상화.
 * <p>
 * <b>두 가지 시그니처</b>
 * <ul>
 *     <li>{@link #send(String, String, String)} — 알림(Notification) only. ZONE_EXIT 등 기존 도메인 사용.</li>
 *     <li>{@link #send(String, String, String, Map)} — 알림 + data payload. SOS 등 알림 탭 시 라우팅 정보가 필요한 도메인 사용.</li>
 * </ul>
 *
 * <b>data payload 용도</b>: 보호자 앱이 알림을 탭했을 때 어디로 라우팅할지 판단하는 메타데이터.
 * 예: {"type":"SOS", "eventId":"123", "wardId":"45", "latitude":"35.335", "longitude":"129.038"}
 *
 * <b>"성공"의 의미</b>: Google FCM 서버가 메시지를 수락한 시점.
 * 사용자 단말 도달 보장 아님 (FCM 명세). 그래서 10분 내 read_at 없으면 SMS 폴백.
 */
public interface FcmSender {

    /**
     * 알림만 발송. data payload 없음.
     */
    boolean send(String fcmToken, String title, String body);

    /**
     * 알림 + data payload 발송. 기본 구현은 data 무시하고 알림만 보냄 (하위호환).
     * 실제 발송 구현체는 data를 FCM Message에 함께 실어 보낸다.
     */
    default boolean send(String fcmToken, String title, String body, Map<String, String> data) {
        return send(fcmToken, title, body);
    }

    /**
     * data payload 없는 발송 — null 안전을 위한 헬퍼.
     */
    static Map<String, String> emptyData() {
        return Collections.emptyMap();
    }

    /**
     * data-only silent push 발송. 알림 표시 없이 백그라운드 동기화 트리거용.
     * 기본 구현은 false (no-op). 실제 발송 구현체에서 override.
     */
    default boolean sendDataOnly(String fcmToken, Map<String, String> data) {
        return false;
    }
}