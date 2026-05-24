package com.oncare.oncare24.medication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * CODEF 처방-약 식별값(처방번호_약품코드)을 HMAC-SHA256 블라인드 인덱스로 변환.
 * 비밀키는 DB가 아닌 환경변수에 둔다(검색 가능 암호화 표준). 정확 일치 검색·중복 방지 전용.
 */
@Component
public class CodefKeyHasher {

    private final byte[] secret;

    public CodefKeyHasher(
            @Value("${oncare.security.codef-bidx-secret:oncare-dev-codef-bidx}") String secret
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 처방번호·약품코드 중 하나라도 비면 null(중복 방지 스킵). */
    public String hash(String prescribeNo, String drugCode) {
        if (prescribeNo == null || prescribeNo.isBlank()
                || drugCode == null || drugCode.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal(
                    (prescribeNo.trim() + "_" + drugCode.trim()).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("codef bidx hashing failed", e);
        }
    }
}