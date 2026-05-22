package com.oncare.oncare24.drugsafety.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Graph RAG 서버에서 반환하는 개별 경고.
 * <p>
 * - type: CONTRAINDICATED / ELDERLY / DUPLICATE / PREGNANCY / OVERDOSE / DURATION
 * - severity: CRITICAL / HIGH / MEDIUM / LOW
 * - rawMessage가 "[간접 위험]"으로 시작하면 Graph RAG가 추론한 간접 위험.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WarningDto {

    private String type;
    private String severity;
    private List<String> involvedIngredients;
    private String rawMessage;
    private String explanation;
}