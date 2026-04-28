package com.oncare.oncare24.guardian.util;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 6자리 숫자 초대 코드 생성기.
 * <p>
 * 100만 가지 경우 + 동시 PENDING 수십~수백 건 가정 시 충돌률 < 0.1%.
 * 그래도 안전하게 5회까지 재시도하고, 그래도 못 만들면 5xx로 던짐(거의 발생 안 함).
 * <p>
 * SecureRandom 사용 — 일반 Random은 시드 예측 가능해 brute-force 보조 정보가 됨.
 */
@Component
@RequiredArgsConstructor
public class InviteCodeGenerator {

    private static final int MAX_ATTEMPTS = 5;
    private static final int CODE_RANGE = 1_000_000; // 000000~999999

    private final SecureRandom random = new SecureRandom();
    private final GuardianWardRepository guardianWardRepository;

    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = String.format("%06d", random.nextInt(CODE_RANGE));
            if (guardianWardRepository.findByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}