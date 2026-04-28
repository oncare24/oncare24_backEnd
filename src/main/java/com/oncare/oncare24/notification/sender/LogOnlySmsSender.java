package com.oncare.oncare24.notification.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Step 8용 SMS 골격 구현체. Step 10에서 CoolSMS 구현체로 교체.
 */
@Slf4j
@Component
public class LogOnlySmsSender implements SmsSender {

    @Override
    public boolean send(String phoneNumber, String body) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("[SMS-SKIP] no phone. body={}", body);
            return false;
        }
        log.info("[SMS-SEND] phone={}, body={}", maskPhone(phoneNumber), body);
        return true;
    }

    /** 010-1234-5678 → 010****5678 (로그 PII 마스킹) */
    private String maskPhone(String phone) {
        if (phone.length() < 8) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}