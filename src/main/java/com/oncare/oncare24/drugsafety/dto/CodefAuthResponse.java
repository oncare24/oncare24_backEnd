package com.oncare.oncare24.drugsafety.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1차 카카오톡 간편인증 응답.
 * <p>
 * jti / twoWayTimestamp 는 2차 확정({@code /confirm}) 시 그대로 전달해야 한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodefAuthResponse {

    private String jti;
    private Long twoWayTimestamp;
    private String transactionId;
}