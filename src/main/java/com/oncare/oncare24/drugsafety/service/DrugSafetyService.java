package com.oncare.oncare24.drugsafety.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.drugsafety.client.GraphRagAuthResponse;
import com.oncare.oncare24.drugsafety.client.GraphRagClient;
import com.oncare.oncare24.drugsafety.client.GraphRagCodefConfirmPayload;
import com.oncare.oncare24.drugsafety.client.GraphRagCodefRequestPayload;
import com.oncare.oncare24.drugsafety.dto.CodefAuthRequest;
import com.oncare.oncare24.drugsafety.dto.CodefAuthResponse;
import com.oncare.oncare24.drugsafety.dto.CodefConfirmRequest;
import com.oncare.oncare24.drugsafety.dto.MedicationAnalysisResponse;
import com.oncare.oncare24.drugsafety.dto.WarningDto;
import com.oncare.oncare24.drugsafety.entity.MedicationAnalysis;
import com.oncare.oncare24.drugsafety.repository.MedicationAnalysisRepository;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oncare.oncare24.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.repository.UserRepository;
import com.oncare.oncare24.drugsafety.dto.PrescriptionDto;
import com.oncare.oncare24.drugsafety.dto.GraphRagConfirmResponse;
import com.oncare.oncare24.medication.dto.AutoRegisterResult;
import com.oncare.oncare24.medication.dto.PrescriptionImportItem;

/**
 * 복약 안전 분석 (Graph RAG) BFF 서비스.
 * <ul>
 *   <li>1차 인증 요청: 외부 호출만 (DB 저장 X).</li>
 *   <li>2차 확정: 외부 호출 + UPSERT 캐시 (UNIQUE on user_id 라서 한 사용자당 최신 1행만).</li>
 *   <li>조회: 캐시된 결과 반환.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugSafetyService {
    private final com.oncare.oncare24.medication.service.MedicationScheduleService medicationScheduleService;
    private static final TypeReference<List<WarningDto>> WARNING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<PrescriptionDto>> PRESCRIPTION_LIST_TYPE = new TypeReference<>() {};
    private final GraphRagClient graphRagClient;
    private final MedicationAnalysisRepository analysisRepository;
    private final DrugSafetyAccessChecker accessChecker;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    /**
     * 1차 카카오톡 간편인증 요청.
     */
    public CodefAuthResponse requestCodefAuth(Long userId, CodefAuthRequest request) {
        accessChecker.ensureSelfAuthAllowed(userId);

        GraphRagAuthResponse response = graphRagClient.requestCodefAuth(
                new GraphRagCodefRequestPayload(
                        request.getIdentity(),
                        request.getUserName(),
                        request.getPhoneNo()
                )
        );

        return CodefAuthResponse.builder()
                .jti(response.jti())
                .twoWayTimestamp(response.twoWayTimestamp())
                .transactionId(response.transactionId())
                .build();
    }

    /**
     * 2차 확정 + 결과 캐시 (UPSERT).
     */
    /**
     * 2차 확정 + 결과 캐시 (UPSERT).
     * <p>
     * User 에 저장된 age / isPregnant 를 Graph RAG payload 에 함께 전달.
     * 회원가입 시 ELDER 는 두 값을 필수로 받지만, 혹시 누락된 경우 INVALID_INPUT_VALUE.
     */
    @Transactional
    public MedicationAnalysisResponse confirmCodefAuth(Long userId, CodefConfirmRequest request) {
        accessChecker.ensureSelfAuthAllowed(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getAge() == null || user.getIsPregnant() == null) {
            log.warn("[DrugSafety] missing age/isPregnant userId={}", userId);
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        GraphRagConfirmResponse response = graphRagClient.confirmCodefAuth(
                new GraphRagCodefConfirmPayload(
                        request.getIdentity(),
                        request.getUserName(),
                        request.getPhoneNo(),
                        request.getJti(),
                        request.getTwoWayTimestamp(),
                        user.getAge(),
                        user.getIsPregnant()
                )
        );

        List<WarningDto> warnings = response.getWarnings();
        List<PrescriptionDto> prescriptions = response.getPrescriptions().stream()
                .filter(p -> !isTopicalEyeDrug(p.getResDrugName()))
                .toList();

        String warningsJson = serializeWarnings(warnings);
        String prescriptionsJson = serializePrescriptions(prescriptions);
        LocalDateTime analyzedAt = LocalDateTime.now();



        analysisRepository.findByUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.overwrite(warningsJson, prescriptionsJson, analyzedAt),
                        () -> analysisRepository.save(
                                MedicationAnalysis.builder()
                                        .userId(userId)
                                        .warningsJson(warningsJson)
                                        .prescriptionsJson(prescriptionsJson)
                                        .analyzedAt(analyzedAt)
                                        .build()
                        )
                );

        List<PrescriptionImportItem> importItems = prescriptions.stream()
                .map(p -> new PrescriptionImportItem(
                        p.getResDrugName(),
                        p.getResDailyDosesNumber(),
                        p.getResTotalDosingdays(),
                        p.getResManufactureDate(),
                        p.getResPrescribeNo(),
                        p.getResDrugCode()
                ))
                .toList();
        AutoRegisterResult autoRegisterResult =
                medicationScheduleService.autoRegisterFromPrescriptions(userId, importItems);


        log.info(
                "[DrugSafety] analysis cached userId={} age={} pregnant={} warnings={} prescriptions={}",
                userId, user.getAge(), user.getIsPregnant(), warnings.size(), prescriptions.size()
        );




        return MedicationAnalysisResponse.builder()
                .warnings(warnings)
                .prescriptions(prescriptions)
                .analyzedAt(analyzedAt)
                .autoRegisterResult(autoRegisterResult)   // ← 추가
                .build();
    }

    /** 점안액·안약 등 외용 약은 복약 일정/안전분석 대상에서 제외 (먹는 약만). */
    private boolean isTopicalEyeDrug(String drugName) {
        if (drugName == null) return false;
        String n = drugName.replaceAll("\\s", "");
        return n.contains("점안") || n.contains("안약");
    }

    /**
     * 캐시된 분석 결과 조회.
     * <p>
     * - {@code ownerId == null} : 본인 결과 조회.
     * - 그 외                  : 보호자 시점. ACCEPTED 매칭 검증 후 통과.
     * - 결과가 없으면 {@link ErrorCode#DRUG_ANALYSIS_NOT_FOUND} (404).
     */
    @Transactional(readOnly = true)
    public MedicationAnalysisResponse getAnalysis(Long viewerId, Long ownerId) {
        Long targetId = (ownerId == null) ? viewerId : ownerId;
        accessChecker.ensureReadAllowed(viewerId, targetId);

        MedicationAnalysis analysis = analysisRepository.findByUserId(targetId)
                .orElseThrow(() -> new CustomException(ErrorCode.DRUG_ANALYSIS_NOT_FOUND));

        return MedicationAnalysisResponse.builder()
                .warnings(deserializeWarnings(analysis.getWarningsJson()))
                .prescriptions(deserializePrescriptions(analysis.getPrescriptionsJson()))
                .analyzedAt(analysis.getAnalyzedAt())
                .build();
    }

    /**
     * 보호자가 피보호자에게 처방전 분석 업데이트 요청 푸시.
     * <p>
     * 권한 검증 후 NotificationService 로 위임.
     * DB 변경 없음(분석 캐시는 피보호자가 분석을 실행해야만 갱신됨).
     */
    @Transactional
    public void requestRefresh(Long guardianId, Long wardId) {
        String guardianName =
                accessChecker.ensureGuardianRefreshRequestAllowed(guardianId, wardId);

        notificationService.notifyDrugAnalysisRefreshRequest(wardId, guardianName);

        log.info("[DrugSafety] refresh request guardianId={} → wardId={}",
                guardianId, wardId);
    }

    private String serializeWarnings(List<WarningDto> warnings) {
        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (JsonProcessingException e) {
            log.error("[DrugSafety] failed to serialize warnings", e);
            throw new CustomException(ErrorCode.DRUG_ANALYSIS_INVALID_RESPONSE);
        }
    }

    private List<WarningDto> deserializeWarnings(String json) {
        try {
            return objectMapper.readValue(json, WARNING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.error("[DrugSafety] failed to deserialize warnings json length={}", json == null ? 0 : json.length(), e);
            throw new CustomException(ErrorCode.DRUG_ANALYSIS_INVALID_RESPONSE);
        }
    }

    private String serializePrescriptions(List<PrescriptionDto> prescriptions) {
        try {
            return objectMapper.writeValueAsString(prescriptions);
        } catch (JsonProcessingException e) {
            log.error("[DrugSafety] failed to serialize prescriptions", e);
            throw new CustomException(ErrorCode.DRUG_ANALYSIS_INVALID_RESPONSE);
        }
    }

    private List<PrescriptionDto> deserializePrescriptions(String json) {
        try {
            return objectMapper.readValue(json, PRESCRIPTION_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.error("[DrugSafety] failed to deserialize prescriptions json length={}", json == null ? 0 : json.length(), e);
            throw new CustomException(ErrorCode.DRUG_ANALYSIS_INVALID_RESPONSE);
        }
    }
}