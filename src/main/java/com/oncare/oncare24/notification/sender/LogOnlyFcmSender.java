package com.oncare.oncare24.notification.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Step 8용 FCM 골격 구현체 (개발/테스트 환경).
 * <p>
 * 실제 발송하지 않고 콘솔 로그로만 남김. data payload도 함께 로깅하여 디버깅에 활용.
 *
 * <b>현재 동작</b>: fcmToken이 비어있으면 false, 아니면 항상 true.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "false", matchIfMissing = false)
public class LogOnlyFcmSender implements FcmSender {

    @Override
    public boolean send(String fcmToken, String title, String body) {
        return send(fcmToken, title, body, FcmSender.emptyData());
    }

    @Override
    public boolean send(String fcmToken, String title, String body, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("[FCM-SKIP] no token. title={}, body={}", title, body);
            return false;
        }
        log.info("[FCM-SEND] token={}..., title={}, body={}, data={}",
                fcmToken.substring(0, Math.min(8, fcmToken.length())),
                title, body,
                data == null ? "{}" : data);
        return true;
    }
}