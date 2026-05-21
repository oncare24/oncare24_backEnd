package com.oncare.oncare24.drugsafety.client;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Graph RAG /drug/codef/request 호출 페이로드.
 * 외부 서버 스펙과 직접 매칭되는 record (필드명 변경 금지).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphRagCodefRequestPayload(
        String identity,
        String userName,
        String phoneNo
) {
}