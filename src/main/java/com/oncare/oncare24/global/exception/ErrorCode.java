package com.oncare.oncare24.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 시스템 전체의 에러 코드 정의.
 * <p>
 * 코드 체계:
 * <ul>
 *     <li>C0xx - 공통 (Common)</li>
 *     <li>A0xx - 인증/인가 (Auth)</li>
 *     <li>U0xx - 사용자 (User)</li>
 *     <li>G0xx - 보호자 연동 (Guardian)</li>
 *     <li>S0xx - 안전 구역 (SafeZone)</li>
 *     <li>L0xx - 위치/모니터링 (Location)</li>
 *     <li>M0xx - 복약 (Medication)</li>
 *     <li>H0xx - 병원/문진 (Hospital)</li>
 *     <li>N0xx - 알림 (Notification)</li>
 * </ul>
 * 새 도메인 추가 시 prefix를 정해 일관성 있게 추가합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // === 공통 ===
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "허용되지 않은 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C004", "타입이 올바르지 않습니다."),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C005", "접근이 거부되었습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C006", "요청한 리소스를 찾을 수 없습니다."),
    REQUEST_BODY_NOT_READABLE(HttpStatus.BAD_REQUEST, "C007", "요청 본문을 파싱할 수 없습니다."),

    // === 인증/인가 (Step 5~6에서 사용) ===
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A004", "이메일 또는 비밀번호가 올바르지 않습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A005", "리프레시 토큰을 찾을 수 없습니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "A006", "리프레시 토큰이 일치하지 않습니다."),

    // === 사용자 ===
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "U003", "이미 사용 중인 전화번호입니다."),


    // === 보호자-피보호자 연동 (Step 7-A / Step 9) ===
    NOT_LINKED_TO_WARD(HttpStatus.FORBIDDEN, "G001", "이 피보호자에 연결되지 않았어요."),
    DUPLICATE_WARD_LINK(HttpStatus.CONFLICT, "G002", "이미 연동된 어르신이에요."),
    PENDING_INVITATION_EXISTS(HttpStatus.CONFLICT, "G003", "이미 초대를 보냈어요. 수락을 기다려 주세요."),
    WARD_NOT_FOUND_BY_PHONE(HttpStatus.NOT_FOUND, "G004", "이 번호로 가입한 어르신을 찾을 수 없어요."),
    SELF_INVITE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "G005", "본인을 초대할 수는 없어요."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "G006", "초대를 찾을 수 없어요."),
    INVITATION_EXPIRED(HttpStatus.GONE, "G007", "초대가 만료됐어요. 다시 보내주세요."),
    INVITATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "G008", "이 초대에 접근할 권한이 없어요."),
    INVITATION_ALREADY_RESPONDED(HttpStatus.CONFLICT, "G009", "이미 처리된 초대예요."),
    ROLE_NOT_GUARDIAN(HttpStatus.FORBIDDEN, "G010", "보호자만 사용할 수 있어요."),
    ROLE_NOT_ELDER(HttpStatus.FORBIDDEN, "G011", "어르신만 사용할 수 있어요."),

    // === 안전 구역 (Step 7) ===
    SAFETY_ZONE_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "안전구역을 찾을 수 없어요."),
    SAFETY_ZONE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "S002", "안전구역은 최대 5개까지 등록할 수 있어요."),
    SAFETY_ZONE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "S003", "이 안전구역을 수정할 권한이 없어요."),
    INVALID_ELDER(HttpStatus.BAD_REQUEST, "S004", "피보호자 정보가 올바르지 않아요."),

    // === 위치/모니터링 (Step 8) ===
    LOCATION_REPORT_FORBIDDEN(HttpStatus.FORBIDDEN, "L001", "본인의 위치만 보고할 수 있어요."),
    LOCATION_NOT_FOUND(HttpStatus.NOT_FOUND, "L002", "위치 기록이 없어요."),
    DEVICE_STATUS_NOT_FOUND(HttpStatus.NOT_FOUND, "L003", "단말 상태 정보를 찾을 수 없어요."),

    // === 알림 (Step 8 골격, Step 10에서 확장) ===
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없어요."),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "N002", "이 알림에 접근할 권한이 없어요."),

    // === 복약 (Medication) ===
    MEDICATION_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "복약 일정을 찾을 수 없습니다."),
    MEDICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "M002", "복약 정보에 접근할 권한이 없습니다."),
    INVALID_MEDICATION_REQUEST(HttpStatus.BAD_REQUEST, "M003", "복약 요청 값이 올바르지 않습니다."),
    MEDICATION_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "복약 기록을 찾을 수 없습니다."),

    // === 카카오 로컬 검색 (Step 11) ===
    KAKAO_SEARCH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "K001", "주소 검색에 실패했어요. 잠시 후 다시 시도해주세요."),

    // === SOS 긴급 호출 (Step 12) ===
    SOS_THROTTLED(HttpStatus.TOO_MANY_REQUESTS, "R001", "방금 호출했어요. 잠시 후 다시 눌러주세요."),
    SOS_LOCATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "R002", "위치 정보가 없어 호출할 수 없어요. 위치 권한을 확인해주세요."),
    // === 복약 안전 분석 (Drug Safety - Graph RAG) ===
    DRUG_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "복약 안전 분석 결과가 없어요. 처방전 분석을 먼저 진행해 주세요."),
    DRUG_ANALYSIS_CODEF_AUTH_FAILED(HttpStatus.BAD_GATEWAY, "D002", "처방전 인증 요청에 실패했어요. 잠시 후 다시 시도해 주세요."),
    DRUG_ANALYSIS_CODEF_CONFIRM_FAILED(HttpStatus.BAD_GATEWAY, "D003", "처방전 조회에 실패했어요. 카카오톡 인증을 다시 시도해 주세요."),
    DRUG_ANALYSIS_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "D004", "복약 분석 서버에 일시적으로 연결할 수 없어요."),
    DRUG_ANALYSIS_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "D005", "복약 분석 서버 응답을 처리할 수 없어요."),
    DRUG_ANALYSIS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "D006", "이 분석 결과에 접근할 권한이 없어요."),

    // === 길안내 (V0xx) ===
    NAVIGATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "V001", "길안내 서비스에 일시적 문제가 발생했습니다."),
    NO_TRANSIT_ROUTE(HttpStatus.NOT_FOUND, "V002", "대중교통 경로를 찾을 수 없습니다. 거리가 너무 가까울 수 있어요.");
// ↑ 마지막은 세미콜론


    private final HttpStatus status;
    private final String code;
    private final String message;
}
