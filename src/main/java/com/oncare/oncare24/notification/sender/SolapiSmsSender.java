package com.oncare.oncare24.notification.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SOLAPI Java SDK 기반 SMS 발송 구현체.
 * <p>
 * <b>활성 조건</b>: {@code sms.enabled=true} 일 때만 빈 등록.
 * <p>
 * <b>"성공" 의미</b>: SOLAPI 서버가 발송 접수 완료한 시점 (status MESSAGE_TYPE 응답).
 * 실제 단말 도달 보장 아님 (통신사 일시 장애, 수신차단 등 가능). 그래서 알림 이중화 흐름은
 * FCM → 10분 미확인 → SMS 폴백으로 이미 설계됨. 본 클래스는 폴백 단계만 담당.
 * <p>
 * <b>본문 길이</b>: 한글 45자 초과 시 자동 LMS 변환 → 비용 ~3배. EscalationService에서
 * 본문 자르기를 수행하므로 여기선 그대로 전송.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SolapiSmsSender implements SmsSender {

    private final DefaultMessageService messageService;

    @Value("${sms.from}")
    private String fromNumber;

    @Override
    public boolean send(String phoneNumber, String body) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("[SMS-SKIP] no phone. body={}", body);
            return false;
        }

        // SOLAPI는 하이픈 제외 숫자만 허용. 방어적으로 정리.
        String to = phoneNumber.replaceAll("\\D", "");

        Message message = new Message();
        message.setFrom(fromNumber);
        message.setTo(to);
        message.setText(body);

        try {
            SingleMessageSentResponse response = messageService.sendOne(
                    new SingleMessageSendingRequest(message)
            );
            log.info("[SMS-SEND] success. messageId={}, statusCode={}, phone={}",
                    response.getMessageId(),
                    response.getStatusCode(),
                    maskPhone(phoneNumber));
            return true;
        } catch (Exception e) {
            // SDK는 발송 실패 시 SolapiMessageNotReceivedException 등 다양한 예외 던짐.
            // 발송 자체는 알림 이중화 흐름의 폴백 단계라서 실패해도 앱 멈추면 안 됨.
            // 실패 사실은 EscalationService가 markSmsSent(false, now) 로 NotificationHistory에 기록.
            log.error("[SMS-SEND] failed. phone={}, error={}",
                    maskPhone(phoneNumber), e.getMessage());
            return false;
        }
    }

    /** 010-1234-5678 → 010****5678 (로그 PII 마스킹) */
    private String maskPhone(String phone) {
        if (phone.length() < 8) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}