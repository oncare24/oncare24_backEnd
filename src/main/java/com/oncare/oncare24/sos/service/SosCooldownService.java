package com.oncare.oncare24.sos.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * SOS 호출 중복 방지 (5초 throttle).
 * <p>
 * <b>왜 5초인가</b>
 * <ul>
 *     <li>시니어가 당황해서 버튼을 연타하는 케이스 차단 — 보호자 앱 알림 폭격 방지</li>
 *     <li>의도치 않은 더블탭 보호 (Pressable + 모달 확인 단계로 1차 차단되지만 서버 측 안전망)</li>
 *     <li>너무 길면 진짜 후속 호출(예: 5초 뒤 더 정확한 GPS로 재호출)을 막아버림</li>
 * </ul>
 *
 * <b>Redis 키 설계</b>
 * <ul>
 *     <li>{@code sos:cooldown:{userId}} → "1" (실제 값은 의미 없음, 존재 여부만 봄)</li>
 *     <li>TTL 5초 자동 만료</li>
 *     <li>SETNX(setIfAbsent)로 race condition 차단 — 동시 호출 시 첫 번째만 성공</li>
 * </ul>
 *
 * <b>fail-open vs fail-closed</b>: Redis 장애 시 throttle은 동작 안 하지만 SOS 자체는 받음.
 * 긴급 도메인이라 안전망보다 도달성 우선.
 */
@Service
@RequiredArgsConstructor
public class SosCooldownService {

    private static final String KEY_PREFIX = "sos:cooldown:";
    private static final Duration COOLDOWN = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;

    /**
     * 쿨다운 키를 set한다. 이미 존재하면 SOS_THROTTLED 예외.
     *
     * @throws CustomException SOS_THROTTLED — 5초 내 재호출
     */
    public void acquireOrThrow(Long userId) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(buildKey(userId), "1", COOLDOWN);

        // setIfAbsent는 Redis 장애 시 null. fail-open: 통과시킴.
        if (Boolean.FALSE.equals(acquired)) {
            throw new CustomException(ErrorCode.SOS_THROTTLED);
        }
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}