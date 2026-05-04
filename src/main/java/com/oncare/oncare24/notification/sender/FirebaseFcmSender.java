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
 * 이렇게 하지 않으면 매 발송마다 같은 무효 토큰으로 실패가 누적됨.
 *
 * <b>트랜잭션 분리</b>: 토큰 정리는 NotificationService 의 트랜잭션과 분리(REQUIRES_NEW).
 * 알림 이력 저장과 토큰 정리는 별개 관심사이고, 토큰 정리 실패가 알림 이력 저장을
 * 롤백시키면 안 됨.
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
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("[FCM-SKIP] no token. title={}, body={}", title, body);
            return false;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();

        try {
            String messageId = firebaseMessaging.send(message);
            log.info("[FCM-SEND] success. messageId={}, token={}...",
                    messageId, fcmToken.substring(0, Math.min(8, fcmToken.length())));
            return true;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            log.warn("[FCM-SEND] failed. errorCode={}, message={}, token={}...",
                    errorCode, e.getMessage(),
                    fcmToken.substring(0, Math.min(8, fcmToken.length())));

            // 토큰이 무효 → DB 에서 제거
            if (errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                invalidateToken(fcmToken);
            }
            return false;
        } catch (Exception e) {
            // 네트워크 일시 오류 등 — 토큰은 유효할 수 있으니 정리 안 함
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
}