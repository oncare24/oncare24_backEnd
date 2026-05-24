package com.oncare.oncare24.notification.sender;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.google.firebase.messaging.AndroidConfig;
import java.util.Map;

/**
 * Firebase Admin SDK 기반 FCM 발송 구현체.
 * <p>
 * <b>활성 조건</b>: {@code fcm.enabled=true}
 *
 * <b>"성공" 의미</b>: Google FCM 서버가 메시지를 수락한 시점 (FirebaseMessaging#send 가 messageId 반환).
 * 사용자 단말 도달 보장 아님. 그래서 10분 미확인 시 SMS 폴백 (EscalationService 담당).
 *
 * <b>토큰 무효화 처리</b>: FCM 서버가 UNREGISTERED / INVALID_ARGUMENT 응답하면
 * 해당 토큰을 가진 유저의 fcm_token 컬럼을 null 로 정리.
 *
 * <b>data payload</b>: putAllData()로 모든 키-값을 함께 발송. 보호자 앱이 알림 탭 시
 * onResponseReceived에서 이 페이로드를 읽어 어디로 라우팅할지 결정 (예: type=SOS면 SosLocationView로).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FirebaseFcmSender implements FcmSender {

    private final FirebaseMessaging firebaseMessaging;
    private final UserRepository userRepository;

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

        Message.Builder builder = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (data != null && !data.isEmpty()) {
            builder.putAllData(data);
        }

        try {
            String messageId = firebaseMessaging.send(builder.build());
            log.info("[FCM-SEND] success. messageId={}, token={}..., dataKeys={}",
                    messageId,
                    fcmToken.substring(0, Math.min(8, fcmToken.length())),
                    data == null ? 0 : data.size());
            return true;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            log.warn("[FCM-SEND] failed. errorCode={}, message={}, token={}...",
                    errorCode, e.getMessage(),
                    fcmToken.substring(0, Math.min(8, fcmToken.length())));

            if (errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                invalidateToken(fcmToken);
            }
            return false;
        } catch (Exception e) {
            log.error("[FCM-SEND] unexpected error. token={}..., message={}",
                    fcmToken.substring(0, Math.min(8, fcmToken.length())), e.getMessage());
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void invalidateToken(String fcmToken) {
        int updated = userRepository.clearFcmToken(fcmToken);
        log.info("[FCM-INVALIDATE] cleared {} user(s) with stale token", updated);
    }

    @Override
    public boolean sendDataOnly(String fcmToken, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("[FCM-SKIP-DATA] no token.");
            return false;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .putAllData(data == null ? java.util.Map.of() : data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .build();

        try {
            String messageId = firebaseMessaging.send(message);
            log.info("[FCM-SEND-DATA] success. messageId={}, dataKeys={}",
                    messageId, data == null ? 0 : data.size());
            return true;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            log.warn("[FCM-SEND-DATA] failed. errorCode={}, message={}",
                    errorCode, e.getMessage());
            if (errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                invalidateToken(fcmToken);
            }
            return false;
        } catch (Exception e) {
            log.error("[FCM-SEND-DATA] unexpected error: {}", e.getMessage());
            return false;
        }
    }
}