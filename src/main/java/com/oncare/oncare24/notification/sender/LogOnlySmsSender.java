package com.oncare.oncare24.notification.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * sms.enabled=false (또는 미설정) 일 때 등록되는 폴백 구현체.
 * 로컬 개발에서 SOLAPI 키 없이도 앱이 뜨도록 해줌.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.enabled", havingValue = "false", matchIfMissing = true)
public class LogOnlySmsSender implements SmsSender {

    @Override
    public boolean send(String phoneNumber, String body) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("[SMS-SKIP] no phone. body={}", body);
            return false;
        }
        log.info("[SMS-SEND-MOCK] phone={}, body={}", maskPhone(phoneNumber), body);
        return true;
    }

    /** 010-1234-5678 → 010****5678 (로그 PII 마스킹) */
    private String maskPhone(String phone) {
        if (phone.length() < 8) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}