package com.oncare.oncare24.auth.jwt;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰의 생성/파싱/검증을 담당하는 컴포넌트.
 * <p>
 * - Access Token: 짧은 만료(30분). subject=userId, claim={role, typ=access}
 * - Refresh Token: 긴 만료(14일). subject=userId, claim={typ=refresh}
 * <p>
 * Refresh Token은 추가로 Redis에 저장되어 Rotation/Logout/Revoke를 지원합니다 ({@link com.oncare.oncare24.auth.service.RefreshTokenService}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYP = "typ";
    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(Long userId, String role) {
        return createToken(userId, role, TYP_ACCESS, jwtProperties.accessTokenValidityInSeconds());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, null, TYP_REFRESH, jwtProperties.refreshTokenValidityInSeconds());
    }

    private String createToken(Long userId, String role, String typ, long validitySeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validitySeconds * 1000L);

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYP, typ)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256);

        if (role != null) {
            builder.claim(CLAIM_ROLE, role);
        }
        return builder.compact();
    }

    /**
     * 토큰 파싱. 만료/위조/형식 오류 시 CustomException 발생.
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("[JWT] Expired token");
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (UnsupportedJwtException | MalformedJwtException
                 | SignatureException | IllegalArgumentException e) {
            log.debug("[JWT] Invalid token: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    public String getRole(String token) {
        return parse(token).get(CLAIM_ROLE, String.class);
    }

    public boolean isAccessToken(String token) {
        return TYP_ACCESS.equals(parse(token).get(CLAIM_TYP, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TYP_REFRESH.equals(parse(token).get(CLAIM_TYP, String.class));
    }
}
