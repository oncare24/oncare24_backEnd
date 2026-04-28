package com.oncare.oncare24.notification.sender;

/**
 * FCM 푸시 발송 추상화.
 * <p>
 * Step 8: {@link LogOnlyFcmSender} 사용 (콘솔 출력만).
 * Step 10: Firebase Admin SDK 기반 구현체로 교체. GeofencingService/EscalationService 코드는 변경 없음.
 *
 * <b>"성공"의 의미</b>: Google FCM 서버가 메시지를 수락한 시점.
 * 사용자 단말 도달 보장 아님 (FCM 명세). 그래서 10분 내 read_at 없으면 SMS 폴백.
 */
public interface FcmSender {

    /**
     * @param fcmToken 수신자 단말의 FCM 등록 토큰. null/빈문자열이면 즉시 false.
     * @param title 알림 제목 (50자 이내 권장)
     * @param body 알림 본문 (200자 이내 권장)
     * @return Google FCM 서버 전달 성공 여부
     */
    boolean send(String fcmToken, String title, String body);
}