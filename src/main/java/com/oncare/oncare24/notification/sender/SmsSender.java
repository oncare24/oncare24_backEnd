package com.oncare.oncare24.notification.sender;

/**
 * SMS 발송 추상화 (CoolSMS 폴백용).
 * <p>
 * Step 8: {@link LogOnlySmsSender} 사용.
 * Step 10: CoolSMS SDK 기반 구현체로 교체.
 *
 * 한 메시지 90바이트(한글 45자) 초과 시 LMS로 자동 분류되며 비용 차이 있음 — 본문 길이 주의.
 */
public interface SmsSender {

    /**
     * @param phoneNumber 수신자 전화번호 (하이픈 제외 숫자만 권장)
     * @param body SMS 본문
     * @return CoolSMS API 발송 접수 성공 여부
     */
    boolean send(String phoneNumber, String body);
}