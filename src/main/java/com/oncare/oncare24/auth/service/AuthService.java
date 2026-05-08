package com.oncare.oncare24.auth.service;

import com.oncare.oncare24.auth.dto.LoginRequest;
import com.oncare.oncare24.auth.dto.ReissueRequest;
import com.oncare.oncare24.auth.dto.SignUpRequest;
import com.oncare.oncare24.auth.dto.SignUpResponse;
import com.oncare.oncare24.auth.dto.TokenResponse;
import com.oncare.oncare24.auth.jwt.JwtProperties;
import com.oncare.oncare24.auth.jwt.JwtProvider;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final MlKemKeyProvisionService mlKemKeyProvisionService;

    /**
     * 회원가입.
     * 전화번호 중복 검사 → 비밀번호 BCrypt 해싱 → 저장.
     */
    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        if (userRepository.existsByPhone(request.phone())) {
            throw new CustomException(ErrorCode.DUPLICATE_PHONE);
        }
        User user = User.builder()
                .phone(request.phone())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(request.role())
                .build();
        User saved = userRepository.save(user);
        mlKemKeyProvisionService.provisionUserMlKemKey(saved.getId());
        log.info("[SignUp] userId={}, phone={}, role={}", saved.getId(), saved.getPhone(), saved.getRole());
        return SignUpResponse.from(saved);
    }

    /**
     * 로그인.
     * 전화번호로 조회 → 비밀번호 매칭 → access/refresh 토큰 발급 → Redis 저장.
     * <p>
     * 로그인 실패 시 사용자 존재 여부와 비밀번호 오류를 구분하지 않습니다 (보안).
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByPhone(request.phone())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        TokenResponse tokens = issueTokens(user);
        log.info("[Login] userId={}", user.getId());
        return tokens;
    }

    /**
     * 토큰 재발급.
     * 1) refresh token의 서명/만료/typ 검증
     * 2) Redis에 저장된 토큰과 동일한지 매칭 검증
     * 3) DB에서 User 다시 조회 (역할 변경 가능성)
     * 4) 새 access/refresh 페어 발급 (Refresh Token Rotation)
     */
    @Transactional(readOnly = true)
    public TokenResponse reissue(ReissueRequest request) {
        if (!jwtProvider.isRefreshToken(request.refreshToken())) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        Long userId = jwtProvider.getUserId(request.refreshToken());

        refreshTokenService.validate(userId, request.refreshToken());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        TokenResponse tokens = issueTokens(user);
        log.info("[Reissue] userId={}", userId);
        return tokens;
    }

    /**
     * 로그아웃. Redis에 저장된 refresh token을 삭제합니다.
     * <p>
     * Access Token은 만료 전까지는 유효합니다. Access Token까지 즉시 무효화하려면
     * 별도 블랙리스트(Redis) 구현이 필요하지만, 현재는 짧은 TTL(30분)에 의존합니다.
     */
    public void logout(Long userId) {
        refreshTokenService.delete(userId);
        log.info("[Logout] userId={}", userId);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);
        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenValidityInSeconds());
    }
}
