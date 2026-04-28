package com.oncare.oncare24.location.service;

import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.entity.DeviceStatus;
import com.oncare.oncare24.location.repository.DeviceStatusRepository;
import com.oncare.oncare24.notification.service.NotificationService;
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
 * 단말 연결 상태 모니터링 배치.
 * <p>
 * <b>설계 근거</b>
 * <ul>
 *     <li>30분 임계값: 메모리 정책 (위치 보고 주기와 일치, 한국ITS학회 노인 보행속도 1.13m/s 근거)</li>
 *     <li>5분 주기: 임계 초과 후 최대 5분 지연으로 보호자 알림. 더 짧게 잡을수록 DB 부하 ↑.
 *         업계 일반적으로 모니터링 배치는 1~5분 사이.</li>
 *     <li>알림 1회 정책: disconnectNotified 플래그로 중복 차단. ACTIVE 복귀 시 자동 리셋.</li>
 * </ul>
 *
 * <b>fixedDelay vs cron</b>: fixedDelay 사용. 이전 실행이 끝난 후 5분 뒤 시작 — 중첩 실행 자연 차단.
 * cron은 시각 기반이라 이전 실행이 길어지면 다음 트리거와 겹칠 수 있음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusService {

    /** 30분 미수신 시 DISCONNECTED로 판정 */
    private static final long DISCONNECT_THRESHOLD_MINUTES = 30;

    /** 배치 주기: 5분 */
    private static final long BATCH_INTERVAL_MS = 5 * 60 * 1000L;

    private final DeviceStatusRepository deviceStatusRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * 5분마다 실행. 트랜잭션은 메서드 단위로 짧게.
     * <p>
     * 처리 대상이 많을 일은 거의 없음(피보호자 수 만큼이고, 그 중에서도 ACTIVE→임계초과 케이스만).
     * 한 번의 트랜잭션에 묶어도 부하 무시 가능.
     */
    @Scheduled(fixedDelay = BATCH_INTERVAL_MS, initialDelay = BATCH_INTERVAL_MS)
    @Transactional
    public void detectDisconnectedDevices() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(DISCONNECT_THRESHOLD_MINUTES);

        List<DeviceStatus> candidates = deviceStatusRepository
                .findByStateAndLastReportAtBefore(DeviceState.ACTIVE, threshold);

        if (candidates.isEmpty()) {
            return; // 정상 — 모두 연결된 상태
        }

        log.info("[DEVICE-BATCH] {} device(s) crossed disconnect threshold", candidates.size());

        for (DeviceStatus device : candidates) {
            device.markDisconnected();

            if (!device.isDisconnectAlreadyNotified()) {
                String wardName = userRepository.findById(device.getUserId())
                        .map(User::getName)
                        .orElse("피보호자");

                notificationService.notifyDeviceDisconnected(device.getUserId(), wardName);
                device.markDisconnectNotified();

                log.info("[DEVICE-DISCONNECTED] user={}, lastReportAt={}",
                        device.getUserId(), device.getLastReportAt());
            }
        }
    }
}