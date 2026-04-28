package com.oncare.oncare24.notification.service;

import com.oncare.oncare24.notification.entity.NotificationHistory;
import com.oncare.oncare24.notification.repository.NotificationHistoryRepository;
import com.oncare.oncare24.notification.sender.SmsSender;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SMS 폴백 에스컬레이션 배치.
 * <p>
 * <b>로직</b>
 * <ol>
 *     <li>1분 주기로 NotificationHistory 스캔</li>
 *     <li>FCM 보낸 지 10분 넘었는데 read_at == null && sms_sent_at == null 인 건 추출</li>
 *     <li>각 건마다 수신자 전화번호로 SMS 1회 발송</li>
 *     <li>sms_sent_at, sms_success 기록</li>
 * </ol>
 *
 * <b>설계 결정 근거</b>
 * <ul>
 *     <li>10분 임계: Apple Find My / Life360 등 시중 앱 표준. 회의 중인 직장인 보호자가 알림 놓치는 평균 시간.</li>
 *     <li>1분 주기: 10분 임계가 분 단위라 배치 주기를 그보다 짧게. 1분 ± 약간의 지연(최대 11분 도달).</li>
 *     <li>SMS는 1회만: 더 보내면 알림 폭격. 그 후엔 앱 내 영구 배너에 의존(프론트 책임).</li>
 *     <li>읽음 처리 시점: 보호자가 알림 탭 → markRead → 이 배치에서 자연스럽게 제외됨.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService {

    /** FCM 발송 후 10분 미확인 시 SMS 폴백 */
    private static final long ESCALATION_THRESHOLD_MINUTES = 10;

    /** 배치 주기: 1분 */
    private static final long BATCH_INTERVAL_MS = 60 * 1000L;

    private final NotificationHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final SmsSender smsSender;

    @Scheduled(fixedDelay = BATCH_INTERVAL_MS, initialDelay = BATCH_INTERVAL_MS)
    @Transactional
    public void escalateUnreadNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ESCALATION_THRESHOLD_MINUTES);

        List<NotificationHistory> candidates = historyRepository.findEscalationCandidates(threshold);

        if (candidates.isEmpty()) {
            return;
        }

        log.info("[ESCALATION-BATCH] {} notification(s) crossed SMS fallback threshold",
                candidates.size());

        LocalDateTime now = LocalDateTime.now();
        for (NotificationHistory history : candidates) {
            User recipient = userRepository.findById(history.getRecipientId()).orElse(null);
            if (recipient == null) {
                // 수신자 계정 삭제 등 — 더 이상 처리할 가치 없음. 발송 실패로 마킹해서 다음 배치에서 제외.
                history.markSmsSent(false, now);
                continue;
            }

            String smsBody = buildSmsBody(history);
            boolean sent = smsSender.send(recipient.getPhone(), smsBody);
            history.markSmsSent(sent, now);

            log.info("[ESCALATION-SMS] historyId={}, recipientId={}, success={}",
                    history.getId(), recipient.getId(), sent);
        }
    }

    /**
     * SMS 본문 작성.
     * <p>
     * 90바이트(한글 45자) 이내 권장. 초과 시 LMS 분류로 비용 증가.
     * 형식: [보살핌] 제목. 본문 일부.
     */
    private String buildSmsBody(NotificationHistory history) {
        String prefix = "[보살핌] ";
        String full = prefix + history.getTitle() + ". " + history.getBody();
        // 한글 45자 = 약 135바이트(UTF-8 기준 한글 1자 3바이트). SMS 90바이트 한도 고려해 보수적으로 자름.
        return full.length() > 40 ? full.substring(0, 40) + "…" : full;
    }
}