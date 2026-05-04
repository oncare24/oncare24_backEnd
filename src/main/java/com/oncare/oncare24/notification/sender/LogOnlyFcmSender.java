package com.oncare.oncare24.notification.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Step 8용 FCM 골격 구현체.
 * <p>
 * 실제 발송하지 않고 콘솔 로그로만 남김. Step 10에서 Firebase Admin SDK 구현체로 교체.
 * <p>
 * <b>현재 동작</b>: fcmToken이 비어있으면 false, 아니면 항상 true (이상 케이스 테스트는 Step 10에서).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "false", matchIfMissing = false)
public class LogOnlyFcmSender implements FcmSender {

    @Override
    public boolean send(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("[FCM-SKIP] no token. title={}, body={}", title, body);
            return false;
        }
        log.info("[FCM-SEND] token={}..., title={}, body={}",
                fcmToken.substring(0, Math.min(8, fcmToken.length())),
                title, body);
        return true;
    }
}