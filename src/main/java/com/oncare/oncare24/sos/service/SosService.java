package com.oncare.oncare24.sos.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.location.entity.LocationReport;
import com.oncare.oncare24.location.repository.LocationReportRepository;
import com.oncare.oncare24.notification.service.NotificationService;
import com.oncare.oncare24.sos.dto.SosEventResponse;
import com.oncare.oncare24.sos.dto.SosTriggerRequest;
import com.oncare.oncare24.sos.entity.SosEvent;
import com.oncare.oncare24.sos.entity.SosLocationSource;
import com.oncare.oncare24.sos.repository.SosEventRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.sos.dto.SosEventDetailResponse;
import java.math.BigDecimal;

/**
 * SOS 긴급 호출 비즈니스 로직.
 * <p>
 * <b>흐름</b>
 * <ol>
 *     <li>역할 검증: ELDER만 호출 가능</li>
 *     <li>5초 쿨다운 검증 (Redis SETNX) — 중복 호출 차단</li>
 *     <li>위치 결정: 요청 좌표 우선 → 없거나 accuracy 초과면 location_reports 최신값 폴백</li>
 *     <li>SosEvent 저장</li>
 *     <li>NotificationService에 broadcast 위임 — 같은 트랜잭션 안에서 보호자 전체에게 발송</li>
 *     <li>NotificationHistory가 만들어졌으므로 EscalationService가 자동으로 SMS 폴백 처리 (10분 임계)</li>
 * </ol>
 *
 * <b>왜 같은 트랜잭션인가</b>: GeofencingService→NotificationService 패턴과 동일하게 일관성 유지.
 * 알림 발송 자체는 FCM 1회 시도 + 결과 history 기록까지가 끝. FCM 실패해도 history row는 남아
 * EscalationService 1분 배치가 SMS로 자동 폴백. 트랜잭션 분리 이득 거의 없음.
 *
 * <b>119 자동 발신은 의도적으로 제외</b>
 * <ul>
 *     <li>오발신 책임 소재 문제 — 의료법·통신사 약관상 자동 발신은 위험</li>
 *     <li>대신 클라이언트가 결과 화면에 "119 직접 전화" 버튼 노출 (Linking.openURL("tel:119"))</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SosService {

    /** 좌표를 신뢰할 만한 최대 GPS 정확도 (미터). LocationReportService와 동일 정책. */
    private static final double MAX_TRUSTED_ACCURACY_METERS = 100.0;

    private final SosEventRepository sosEventRepository;
    private final UserRepository userRepository;
    private final LocationReportRepository locationReportRepository;
    private final NotificationService notificationService;
    private final SosCooldownService sosCooldownService;
    private final GuardianWardRepository guardianWardRepository;

    @Transactional
    public SosEventResponse trigger(Long currentUserId, SosTriggerRequest request) {
        User caller = assertCallerIsElder(currentUserId);
        sosCooldownService.acquireOrThrow(currentUserId);

        ResolvedLocation loc = resolveLocation(currentUserId, request);

        SosEvent event = SosEvent.builder()
                .wardId(currentUserId)
                .latitude(loc.latitude)
                .longitude(loc.longitude)
                .locationSource(loc.source)
                .build();
        SosEvent saved = sosEventRepository.save(event);

        int notified = notificationService.notifySosBroadcast(
                currentUserId,
                caller.getName(),
                saved.getId(),
                saved.getLatitude(),
                saved.getLongitude()
        );
        saved.markNotified(notified);

        log.info("[SOS-TRIGGER] eventId={}, wardId={}, locationSource={}, notified={}",
                saved.getId(), currentUserId, loc.source, notified);

        return SosEventResponse.from(saved);
    }

    /**
     * SOS 이벤트 상세 조회. 보호자가 알림 탭 → SosLocationView 진입 시 사용.
     * <p>
     * <b>권한 정책</b>: 호출자가 해당 ward와 ACCEPTED로 연결된 보호자여야 함.
     * 본인(피보호자)이 자기 호출 이력을 보는 것도 허용 — 내가 보낸 SOS의 결과 화면 재진입 시 사용 가능.
     */
    @Transactional(readOnly = true)
    public SosEventDetailResponse getDetail(Long currentUserId, Long eventId) {
        SosEvent event = sosEventRepository.findById(eventId)
                .orElseThrow(() -> new com.oncare.oncare24.global.exception.CustomException(
                        com.oncare.oncare24.global.exception.ErrorCode.RESOURCE_NOT_FOUND));

        Long wardId = event.getWardId();

        // 본인 호출이 아니면, 그 ward와 연결된 ACCEPTED 보호자여야만 열람 가능
        if (!wardId.equals(currentUserId)) {
            boolean linked = guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                    currentUserId, wardId,
                    com.oncare.oncare24.guardian.entity.GuardianWardStatus.ACCEPTED
            );
            if (!linked) {
                throw new com.oncare.oncare24.global.exception.CustomException(
                        com.oncare.oncare24.global.exception.ErrorCode.NOT_LINKED_TO_WARD);
            }
        }

        User ward = userRepository.findById(wardId)
                .orElseThrow(() -> new com.oncare.oncare24.global.exception.CustomException(
                        com.oncare.oncare24.global.exception.ErrorCode.USER_NOT_FOUND));

        return SosEventDetailResponse.of(event, ward);
    }

    // ============================================================
    // 검증 헬퍼
    // ============================================================

    private User assertCallerIsElder(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ELDER) {
            throw new CustomException(ErrorCode.ROLE_NOT_ELDER);
        }
        return user;
    }

    // ============================================================
    // 위치 해석
    // ============================================================

    /**
     * 요청 본문 좌표 + accuracy 검증 → 부적합하면 location_reports 최신값 폴백 → 그것도 없으면 NONE.
     */
    private ResolvedLocation resolveLocation(Long userId, SosTriggerRequest req) {
        // 1순위: 요청 본문에 좌표 + accuracy 신뢰 가능
        if (req.latitude() != null
                && req.longitude() != null
                && (req.accuracy() == null || req.accuracy() <= MAX_TRUSTED_ACCURACY_METERS)) {
            return new ResolvedLocation(req.latitude(), req.longitude(), SosLocationSource.CLIENT);
        }

        // 2순위: location_reports 최신값 폴백
        return locationReportRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(this::fromLocationReport)
                .orElse(ResolvedLocation.NONE);
    }

    private ResolvedLocation fromLocationReport(LocationReport r) {
        return new ResolvedLocation(r.getLatitude(), r.getLongitude(), SosLocationSource.FALLBACK);
    }

    /**
     * private record — resolveLocation 결과를 한 묶음으로 반환하기 위한 내부 타입.
     */
    private record ResolvedLocation(BigDecimal latitude, BigDecimal longitude, SosLocationSource source) {
        static final ResolvedLocation NONE = new ResolvedLocation(null, null, SosLocationSource.NONE);
    }
}