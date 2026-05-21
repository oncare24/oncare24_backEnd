package com.oncare.oncare24.drugsafety.client;

import com.oncare.oncare24.drugsafety.config.GraphRagProperties;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.oncare.oncare24.drugsafety.dto.GraphRagConfirmResponse;
/**
 * Graph RAG NestJS 서버 (이정현) 연동 클라이언트.
 * <p>
 * 책임:
 * - 외부 호출만 담당 (트랜잭션, 비즈니스 로직, 캐싱은 service 계층).
 * - 외부 응답을 우리 도메인 DTO 로 변환.
 * - 외부 장애를 우리 ErrorCode 로 변환.
 * <p>
 * 민감 정보(주민번호, 전화번호)는 절대 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class GraphRagClient {

    private static final String CODEF_REQUEST_PATH = "/drug/codef/request";
    private static final String CODEF_CONFIRM_PATH = "/drug/codef/confirm";

    private final RestClient client;
    private final GraphRagProperties properties;

    public GraphRagClient(
            @Qualifier("graphRagRestClient") RestClient client,
            GraphRagProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 1차 카카오톡 간편인증 요청.
     */
    public GraphRagAuthResponse requestCodefAuth(GraphRagCodefRequestPayload payload) {
        ensureEnabled();
        log.info("[GraphRAG] codef auth request name={}", payload.userName());

        try {
            GraphRagAuthResponse response = client.post()
                    .uri(CODEF_REQUEST_PATH)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("[GraphRAG] auth request failed status={}", res.getStatusCode());
                        throw new CustomException(ErrorCode.DRUG_ANALYSIS_CODEF_AUTH_FAILED);
                    })
                    .body(GraphRagAuthResponse.class);

            if (response == null || response.jti() == null || response.twoWayTimestamp() == null) {
                throw new CustomException(ErrorCode.DRUG_ANALYSIS_INVALID_RESPONSE);
            }
            log.info("[GraphRAG] codef auth response jti={}", response.jti());
            return response;
        } catch (ResourceAccessException e) {
            log.error("[GraphRAG] auth request connection failure", e);
            throw new CustomException(ErrorCode.DRUG_ANALYSIS_SERVER_UNAVAILABLE);
        }
    }

    /**
     * 2차 카카오톡 인증 확정 + 처방전 분석.
     * 응답은 Warning 배열.
     */
    public GraphRagConfirmResponse confirmCodefAuth(GraphRagCodefConfirmPayload payload) {
        ensureEnabled();
        log.info("[GraphRAG] codef confirm request name={} jti={}", payload.userName(), payload.jti());

        try {
            GraphRagConfirmResponse response = client.post()
                    .uri(CODEF_CONFIRM_PATH)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("[GraphRAG] confirm failed status={} body={}", res.getStatusCode(), body);
                        throw new CustomException(ErrorCode.DRUG_ANALYSIS_CODEF_CONFIRM_FAILED);
                    })
                    .body(GraphRagConfirmResponse.class);

            // null 방어
            return GraphRagConfirmResponse.builder()
                    .warnings(response == null || response.getWarnings() == null
                            ? List.of() : response.getWarnings())
                    .prescriptions(response == null || response.getPrescriptions() == null
                            ? List.of() : response.getPrescriptions())
                    .message(response == null ? null : response.getMessage())
                    .build();
        } catch (ResourceAccessException e) {
            log.error("[GraphRAG] confirm connection failure", e);
            throw new CustomException(ErrorCode.DRUG_ANALYSIS_SERVER_UNAVAILABLE);
        }
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            log.warn("[GraphRAG] disabled via configuration");
            throw new CustomException(ErrorCode.DRUG_ANALYSIS_SERVER_UNAVAILABLE);
        }
    }
}