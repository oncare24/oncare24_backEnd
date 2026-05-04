package com.oncare.oncare24.notification.service;

import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.notification.entity.NotificationHistory;
import com.oncare.oncare24.notification.entity.NotificationType;
import com.oncare.oncare24.notification.repository.NotificationHistoryRepository;
import com.oncare.oncare24.notification.sender.FcmSender;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 발행 도메인 서비스.
 * <p>
 * <b>역할 경계</b>
 * <ul>
 *     <li>"무엇을 누구에게 보낼 것인가"의 비즈니스 로직 (수신자 식별, 메시지 작성)</li>
 *     <li>NotificationHistory 트랜잭션 관리</li>
 *     <li>1차 FCM 발송 + 결과 기록 (SMS 폴백은 EscalationService가 담당 — 책임 분리)</li>
 * </ul>
 * <b>역할 외</b>
 * <ul>
 *     <li>실제 FCM/SMS 송신 — Sender 인터페이스에 위임</li>
 *     <li>SMS 에스컬레이션 결정 — EscalationService가 1분 배치로 수행 (Phase 4)</li>
 * </ul>
 *
 * <b>호출 패턴</b>
 * <pre>
 *   GeofencingService     → notifyZoneExit(...)
 *   DeviceStatusService   → notifyDeviceDisconnected(...)
 *   (후속 Step)           → notifySos / notifyMedicationMissed / ...
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationHistoryRepository historyRepository;
    private final GuardianWardRepository guardianWardRepository;
    private final UserRepository userRepository;
    private final FcmSender fcmSender;

    // ============================================================
    // 도메인별 진입점
    // ============================================================

    /**
     * 안전구역 이탈 알림. INSIDE → OUTSIDE 전환 시 GeofencingService가 호출.
     */
    @Transactional
    public void notifyZoneExit(Long wardId, Long zoneId, String wardName, String zoneName) {
        String title = "안전구역 이탈 알림";
        String body = String.format("%s님이 %s에서 벗어났어요.", wardName, zoneName);
        broadcastToGuardians(wardId, NotificationType.ZONE_EXIT, title, body, zoneId);
    }

    /**
     * 단말 미연결 알림. ACTIVE → DISCONNECTED 전환 시 DeviceStatusService가 호출.
     */
    @Transactional
    public void notifyDeviceDisconnected(Long wardId, String wardName) {
        String title = "위치 확인 불가";
        String body = String.format("%s님의 위치가 30분 이상 확인되지 않고 있어요.", wardName);
        broadcastToGuardians(wardId, NotificationType.DEVICE_DISCONNECTED, title, body, null);
    }

    /**
     * 보호자→피보호자 초대 알림.
     * <p>
     * <b>호출 시점</b>: InvitationService.create()에서 초대가 PENDING으로 발행되거나 reInvite된 직후.
     *
     * <b>broadcastToGuardians와 다른 이유</b>:
     * <ul>
     *     <li>수신자가 ACCEPTED 보호자가 아니라 특정 피보호자 1명</li>
     *     <li>아직 매칭이 ACCEPTED가 아닌 상태 (PENDING)</li>
     * </ul>
     * 그래서 별도 진입점.
     */
    @Transactional
    public void notifyWardInvitation(Long wardId, String guardianName) {
        User ward = userRepository.findById(wardId).orElse(null);
        if (ward == null) {
            log.warn("[NOTIFY-SKIP] ward {} not found for invitation notification", wardId);
            return;
        }

        String title = "새 보호자 초대";
        String body = String.format("%s님이 보호자가 되고 싶어해요. 받은 초대에서 확인해 주세요.", guardianName);

        NotificationHistory history = NotificationHistory.builder()
                .recipientId(wardId)
                .wardId(wardId)            // 자기 자신이 ward인 케이스. 의미상 wardId=recipientId.
                .type(NotificationType.WARD_INVITATION)
                .title(title)
                .body(body)
                .relatedZoneId(null)
                .build();
        history = historyRepository.save(history);

        boolean fcmOk = fcmSender.send(ward.getFcmToken(), title, body);
        history.markFcmSent(fcmOk, LocalDateTime.now());
    }

    /**
     * SOS 긴급 호출 알림. SosService.trigger()가 호출.
     * <p>
     * <b>본문 차별화</b>: ZONE_EXIT보다 더 강한 표현. 좌표 정보가 있으면 "현재 위치와 함께 보호자에게 알렸어요" 식으로
     * 받는 보호자가 즉각 인지 가능하도록.
     *
     * <b>FCM data payload</b>: 보호자 앱이 알림 탭 시 SosLocationView로 자동 라우팅하기 위해
     * eventId, wardId, latitude, longitude를 함께 전달. 클라이언트의 expo-notifications
     * response listener가 이 데이터를 읽어 화면 이동 처리.
     *
     * <b>NotificationHistory.sosEventId</b>: 알림센터 카드 탭 시(인앱 흐름) 같은 라우팅을 가능하게 하는 키.
     *
     * @param wardId         호출자 (피보호자)
     * @param wardName       알림 본문에 들어갈 피보호자 이름
     * @param sosEventId     방금 저장된 SosEvent의 ID
     * @param latitude       호출 좌표 위도 (없을 수 있음)
     * @param longitude      호출 좌표 경도 (없을 수 있음)
     * @return 실제 발송된 보호자 수
     */
    @Transactional
    public int notifySosBroadcast(
            Long wardId,
            String wardName,
            Long sosEventId,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude
    ) {
        boolean hasLocation = latitude != null && longitude != null;

        String title = "긴급 호출";
        String body = hasLocation
                ? String.format("%s님이 긴급 호출을 보냈어요. 위치를 확인해주세요.", wardName)
                : String.format("%s님이 긴급 호출을 보냈어요. 즉시 연락해보세요.", wardName);

        List<GuardianWard> links = guardianWardRepository
                .findByWardIdAndStatus(wardId, GuardianWardStatus.ACCEPTED);

        if (links.isEmpty()) {
            log.warn("[NOTIFY-SOS-SKIP] ward {} has no accepted guardians.", wardId);
            return 0;
        }

        // FCM data payload — 보호자 앱이 알림 탭 시 라우팅 정보로 사용
        java.util.Map<String, String> dataPayload = new java.util.HashMap<>();
        dataPayload.put("type", "SOS");
        dataPayload.put("eventId", String.valueOf(sosEventId));
        dataPayload.put("wardId", String.valueOf(wardId));
        if (hasLocation) {
            dataPayload.put("latitude", latitude.toPlainString());
            dataPayload.put("longitude", longitude.toPlainString());
        }

        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        for (GuardianWard link : links) {
            User guardian = userRepository.findById(link.getGuardianId()).orElse(null);
            if (guardian == null) continue;

            NotificationHistory history = NotificationHistory.builder()
                    .recipientId(guardian.getId())
                    .wardId(wardId)
                    .type(NotificationType.SOS)
                    .title(title)
                    .body(body)
                    .relatedZoneId(null)
                    .sosEventId(sosEventId)
                    .build();
            history = historyRepository.save(history);

            boolean fcmOk = fcmSender.send(guardian.getFcmToken(), title, body, dataPayload);
            history.markFcmSent(fcmOk, now);
            sent++;

            log.info("[NOTIFY-SOS] historyId={}, guardianId={}, fcmOk={}",
                    history.getId(), guardian.getId(), fcmOk);
        }
        return sent;
    }
    // ============================================================
    // 공통 발행 로직
    // ============================================================

    /**
     * 해당 ward와 ACCEPTED 상태로 연결된 모든 보호자에게 동시 발송.
     * <p>
     * 보호자 한 명당 history row 1개 + FCM 1회. SMS 폴백은 EscalationService 1분 배치가 처리.
     */
    private void broadcastToGuardians(
            Long wardId,
            NotificationType type,
            String title,
            String body,
            Long relatedZoneId
    ) {
        List<GuardianWard> links = guardianWardRepository
                .findByWardIdAndStatus(wardId, GuardianWardStatus.ACCEPTED);

        if (links.isEmpty()) {
            log.warn("[NOTIFY-SKIP] ward {} has no accepted guardians. type={}", wardId, type);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (GuardianWard link : links) {
            User guardian = userRepository.findById(link.getGuardianId()).orElse(null);
            if (guardian == null) continue;

            NotificationHistory history = NotificationHistory.builder()
                    .recipientId(guardian.getId())
                    .wardId(wardId)
                    .type(type)
                    .title(title)
                    .body(body)
                    .relatedZoneId(relatedZoneId)
                    .build();
            history = historyRepository.save(history);

            boolean fcmOk = fcmSender.send(guardian.getFcmToken(), title, body);
            history.markFcmSent(fcmOk, now);
        }
    }
}