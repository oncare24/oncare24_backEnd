package com.oncare.oncare24.hospital.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.hospital.dto.MedicalChatRequest;
import com.oncare.oncare24.hospital.dto.MedicalChatResponse;
import com.oncare.oncare24.hospital.service.MedicalChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 멀티턴 LLM 문진 채팅 API.
 * <p>
 * <b>이 버전은 진단용 로그 추가됨</b>: 응답 직전 응답 본문 전체를 콘솔에 출력.
 * 프론트가 못 받는 게 백엔드 응답 문제인지 프론트 파싱 문제인지 진단.
 */
@Slf4j
@RestController
@RequestMapping("/api/medical-chat")
@RequiredArgsConstructor
@Tag(name = "Medical Chat", description = "LLM 멀티턴 문진 채팅")
@SecurityRequirement(name = "BearerAuth")
public class MedicalChatController {

    private final MedicalChatService medicalChatService;
    private final ObjectMapper objectMapper;

    @PostMapping("/turn")
    @Operation(
            summary = "멀티턴 채팅 한 턴 처리",
            description = """
                    클라이언트가 보낸 history + 현재 사용자 메시지를 LLM에 전달하여 다음 봇 응답을 생성합니다.

                    응답 두 가지 모드:
                    1) done=false → reply에 후속 질문 (UI는 다음 사용자 입력 대기)
                    2) done=true → reply에 마무리 멘트 + result에 진료과 + 병원 리스트
                                    (UI는 결과 화면으로 이동)
                    """
    )
    public ApiResponse<MedicalChatResponse> turn(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MedicalChatRequest request
    ) {
        MedicalChatResponse response = medicalChatService.processTurn(userDetails.getUserId(), request);

        // ★ 진단 로그: 응답 전체 본문 + done/result 여부 출력
        log.info("[MedicalChat-DEBUG] done={}, replyLen={}, resultIsNull={}",
                response.done(),
                response.reply() != null ? response.reply().length() : 0,
                response.result() == null);

        if (response.done() && response.result() != null) {
            log.info("[MedicalChat-DEBUG] result.department={}, hospitalsCount={}",
                    response.result().department(),
                    response.result().hospitals() != null ? response.result().hospitals().size() : 0);
        }

        try {
            String responseJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(ApiResponse.success(response));
            log.info("[MedicalChat-DEBUG] full response body:\n{}", responseJson);
        } catch (Exception e) {
            log.warn("[MedicalChat-DEBUG] failed to serialize response: {}", e.getMessage());
        }

        return ApiResponse.success(response);
    }
}