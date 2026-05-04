package com.oncare.oncare24.user.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.user.dto.UserResponse;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 현재 로그인한 사용자의 정보 조회.
     * 로그인 직후 프론트에서 user 정보 캐싱용으로 호출.
     */
    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /**
     * FCM 토큰 등록/갱신.
     * <p>
     * 같은 토큰이 이미 저장돼 있으면 dirty checking 으로 UPDATE 발생 안 함 (JPA 기본 동작).
     * 다른 기기에서 같은 계정으로 로그인 시 토큰이 갱신되면 이전 기기 토큰은 덮어쓰여짐 — 의도된 동작 (Step 8 단계에서는 1유저=1기기 정책).
     *
     * <b>왜 별도 트랜잭션</b>: getMe와 분리. 변경(write) 메서드는 readOnly=false 가 기본.
     */
    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.updateFcmToken(fcmToken);
        log.info("[FCM-TOKEN-REGISTER] userId={}, token={}...",
                userId, fcmToken.substring(0, Math.min(8, fcmToken.length())));
    }
}