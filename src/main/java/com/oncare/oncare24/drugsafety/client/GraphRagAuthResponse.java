package com.oncare.oncare24.drugsafety.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Graph RAG /drug/codef/request 응답.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphRagAuthResponse(
        String jti,
        Long twoWayTimestamp,
        String transactionId
) {
}