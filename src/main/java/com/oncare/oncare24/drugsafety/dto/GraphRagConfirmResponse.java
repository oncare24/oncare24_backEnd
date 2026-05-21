package com.oncare.oncare24.drugsafety.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Graph RAG /drug/codef/confirm 응답.
 * 빈 처방이면 warnings/prescriptions 모두 빈 배열. message는 안내용(optional).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphRagConfirmResponse {
    private List<WarningDto> warnings;
    private List<PrescriptionDto> prescriptions;
    private String message;
}