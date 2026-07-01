package com.oncare.oncare24.medication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * CODEF 처방-약 식별값을 HMAC-SHA256 블라인드 인덱스로 변환.
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

    /** 처방번호·약품코드 중 하나라도 비면 null. 이 경우 호출부는 {@link #hashFallback}로 대체 키를 만든다. */
    public String hash(String prescribeNo, String drugCode) {
        if (prescribeNo == null || prescribeNo.isBlank()
                || drugCode == null || drugCode.isBlank()) {
            return null;
        }
        return hmacHex(prescribeNo.trim() + "_" + drugCode.trim());
    }

    /**
     * 처방번호/약품코드가 없을 때 쓰는 대체 중복 방지 키.
     * 약명 + 조제일 + 1일 복용횟수로 안정적으로 생성 → 재분석 시 동일 입력이면 같은 키가 되어
     * 중복 등록을 차단한다. 정상 키와 입력 형식이 달라 충돌하지 않는다.
     */
    public String hashFallback(String drugName, String manufactureDate, String dailyDoses) {
        String raw = "fb|" + norm(drugName) + "|" + norm(manufactureDate) + "|" + norm(dailyDoses);
        return hmacHex(raw);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    private String hmacHex(String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("codef bidx hashing failed", e);
        }
    }
}
