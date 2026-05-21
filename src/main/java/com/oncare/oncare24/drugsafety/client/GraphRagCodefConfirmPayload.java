package com.oncare.oncare24.drugsafety.client;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Graph RAG /drug/codef/confirm 호출 페이로드.
 * twoWayTimestamp 는 Number 타입으로 직렬화돼야 함 (외부 스펙).
 * age / isPregnant 는 Graph RAG 의 ELDERLY · PREGNANCY 판정에 사용.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphRagCodefConfirmPayload(
        String identity,
        String userName,
        String phoneNo,
        String jti,
        Long twoWayTimestamp,
        Integer age,
        Boolean isPregnant
) {
}