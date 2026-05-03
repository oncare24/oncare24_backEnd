package com.oncare.oncare24.hospital.controller;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 멀티턴 LLM 문진 채팅 API.
 * <p>
 * 클라이언트는 매 사용자 입력마다 이 엔드포인트를 호출하여 history + 현재 메시지를 보낸다.
 * 백엔드 LLM이 후속 질문을 생성하거나, 충분한 정보가 모이면 진료과 분석 + 병원 추천까지 반환한다.
 *
 * <p><b>기존 {@code /api/hospitals/recommend}와 차이</b>:
 * <ul>
 *   <li>{@code /api/hospitals/recommend} - 단일 메시지 분석 (음성 등 단발성). 1회 LLM 호출.</li>
 *   <li>{@code /api/medical-chat/turn} - 멀티턴 채팅. 매 턴마다 LLM 호출, 동적 후속 질문 생성.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/medical-chat")
@RequiredArgsConstructor
@Tag(name = "Medical Chat", description = "LLM 멀티턴 문진 채팅")
@SecurityRequirement(name = "BearerAuth")
public class MedicalChatController {

    private final MedicalChatService medicalChatService;

    @PostMapping("/turn")
    @Operation(
            summary = "멀티턴 채팅 한 턴 처리",
            description = """
                    클라이언트가 보낸 history + 현재 사용자 메시지를 LLM에 전달하여 다음 봇 응답을 생성합니다.

                    응답 두 가지 모드:
                    1) done=false → reply에 후속 질문 (UI는 다음 사용자 입력 대기)
                    2) done=true → reply에 마무리 멘트 + result에 진료과 + 병원 리스트
                                    (UI는 결과 화면으로 이동)

                    무의미한 입력(인사, 잡담), 의료 무관 질문은 LLM이 자동으로 거름망하여 의료 주제로 환원합니다.

                    LLM 실패 시 폴백:
                    - user 발화 0~1턴: 고정 후속 질문
                    - user 발화 2턴+: 누적 텍스트로 키워드 분석 후 done=true
                    - user 발화 5턴 초과: 강제 done=true (가정의학과 폴백)
                    """
    )
    public ApiResponse<MedicalChatResponse> turn(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MedicalChatRequest request
    ) {
        return ApiResponse.success(medicalChatService.processTurn(userDetails.getUserId(), request));
    }
}
