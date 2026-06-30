# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 언어

모든 응답과 설명은 항상 한국어로 작성한다.

## 변경 이력 기록 (필수)

**코드를 수정·추가·삭제하는 작업을 완료하면 항상 `CHANGELOG.md` 맨 위에 항목을 추가한다.**
- 한 작업(논리적 단위) = 한 항목. `날짜 · 제목` 헤더 아래에 **무엇을 / 왜 / 영향 범위(파일·API·설정) / 검증 결과**를 적는다.
- 날짜는 절대값(YYYY-MM-DD)으로 쓴다.
- 사소한 오타·포맷 수정만 한 경우는 생략 가능하나, API/스키마/설정/의존성/동작이 바뀌면 반드시 기록한다.
- 아키텍처·암호화 흐름의 구조적 변경은 `CHANGELOG.md`에 요약 + 필요 시 `change_summary.md` 본문도 갱신한다.

## 프로젝트

**보살핌 (Oncare24)** — 고령자 케어를 위한 통합 스마트 케어 백엔드. Spring Boot 3.4.5 / Java 17 / Gradle. 핵심 기능: **종단간 암호화된** 활동 이벤트 기반 복약 순응도·미활동 분석, 보호자↔피보호자 연동, 위치 모니터링, 안전구역, 복약 안전 분석(Graph RAG), 푸시/SMS 알림.

> `README.md`의 "구현 단계" 체크리스트는 오래되어 부정확하다(Step 1~3만 완료된 것처럼 표기). 실제 코드베이스에는 완성된 도메인이 다수 존재하므로, 그 체크리스트가 아니라 코드를 신뢰할 것. `change_summary.md`가 암호화/분석 흐름을 가장 정확하게 설명하는 문서다.

## 명령어

```bash
# MySQL + Redis 기동 (앱 실행 전 필수)
docker compose up -d

# 앱 실행 (기본 프로파일: dev, 포트 8080)
./gradlew bootRun

# 빌드 / 전체 테스트
./gradlew build
./gradlew test

# 단일 테스트 클래스 또는 메서드 실행
./gradlew test --tests "com.oncare.oncare24.auth.service.AuthServiceTest"
./gradlew test --tests "com.oncare.oncare24.auth.service.AuthServiceTest.login_success"

# 헬스 체크 & API 문서
curl http://localhost:8080/api/health
# Swagger UI: http://localhost:8080/swagger-ui.html
```

별도의 lint 단계는 없으며, 컴파일러와 테스트에 의존한다. Windows에서 `./gradlew`가 동작하지 않으면 `gradlew.bat`을 사용한다.

### 환경 설정
`cp .env.example .env` 후 시크릿을 채운다. Spring은 이 값들을 환경 변수로 읽는다(`application.yml`의 플레이스홀더 참고). 활성 프로파일은 `SPRING_PROFILES_ACTIVE`(`dev`|`prod`)로 지정한다. 외부 연동은 모두 `mock`/`enabled` 토글이 있어 실제 키 없이도 앱이 기동된다: `FCM_ENABLED`, `SMS_ENABLED`, `GRAPHRAG_ENABLED`, `ONCARE_SECURITY_CRYPTO_ENABLED`.

## 아키텍처

### 패키지 구조
`com.oncare.oncare24.{domain}` 아래 도메인별 패키지로 구성하며, 각 패키지는 `controller / service / repository / entity / dto`로 분리한다(외부 API를 호출하는 도메인은 `client / config / util` 추가). `global/`은 공통 횡단 코드, `security/`는 암호화/KMS 레이어를 담는다. 도메인: `auth`, `guardian`, `elder`, `medication`, `location`, `inactivity`, `analysis`, `notification`, `kakao`, `drugsafety`, `safetyzone`, `sos`.

### 컨벤션 (신규 코드에서 반드시 준수)
- **응답**: 컨트롤러는 `ApiResponse.success(data)`를 반환한다. 에러 응답을 직접 만들지 말 것 — `throw new CustomException(ErrorCode.X)`로 던지면 `GlobalExceptionHandler`가 `{ "success": false, "error": { "code", "message" } }` 형식으로 처리한다.
- **에러 코드**: `ErrorCode` enum에 도메인 prefix로 추가한다(C=공통, A=인증, U=사용자, G=보호자, S=안전구역, L=위치, M=복약, N=알림, D=복약안전, R=SOS).
- **엔티티**는 `created_at`/`updated_at`을 위해 `BaseTimeEntity`를 상속한다(JPA Auditing, `JpaAuditingConfig`에서 활성화).

### 인증 (Spring Security + JWT)
무상태(stateless). `SecurityConfig` 필터 체인: `JwtExceptionFilter` → `JwtAuthenticationFilter` → ... `JwtExceptionFilter`가 더 바깥에 위치하여 JWT 인증 과정에서 던져진 예외를 포맷된 응답으로 변환한다. **공개 엔드포인트**(토큰 불필요): `POST /api/auth/{signup,login,reissue}`, `GET /api/health/**`, Swagger 경로. 그 외 모든 요청은 Bearer 토큰이 필요하다. 액세스 토큰 30분, 리프레시 토큰 14일(리프레시 토큰은 Redis에 저장). 비밀번호는 BCrypt.

### 암호화 활동 로그 + 분석 — 핵심 데이터 흐름
가장 중요하고 일반적이지 않은 아키텍처이므로 반드시 이해할 것. 전체 명세는 `change_summary.md`를 참고한다.

- **원천 이벤트는 암호화되어 저장된다.** 민감한 복약/위치/디바이스 상세 값은 도메인 테이블에 평문으로 저장되지 **않는다.** 도메인 row(`medication_schedule`, `medication_log`, `location_report`, `device_status`)에는 라우팅/메타데이터 컬럼만 남는다(`ward_id`, `is_active`, 타임스탬프, `encrypted_activity_log_id`, `source_table`/`source_id`). 실제 payload는 `encrypted_activity_log.encrypted_package`에 암호화되어 저장되며, `event_type`은 {`MEDICATION_EVENT`, `LOCATION_EVENT`, `DEVICE_EVENT`} 중 하나다.
- **암호화 경로**: `CommonCryptoService` → `JnaCryptoFfiClient` → Rust FFI(`native/crypto_ffi.dll`, JNA). 키는 **OpenBao**(Vault 호환 KV)에 저장되며 MySQL에는 저장되지 않는다: 사용자별 ML-KEM 키쌍(`cap2/users/{userId}/mlkem`), 날짜별 `DataKey`(`cap2/data-keys/{keyId}`), 소유자별 `KeyEnvelope`(`cap2/key-envelopes/...`). 프로비저닝은 회원가입 시점(`MlKemKeyProvisionService`)과 보호자 초대 수락 시점(`KeyEnvelopeProvisionService` — 수락된 보호자에게 복호화 권한 부여)에 일어난다. payload는 ward와 guardian **양쪽 공개키로** 암호화되어 `ACCEPTED` 보호자가 복호화할 수 있다.
- **분석은 이벤트 소싱 방식이며 최신 상태값만 저장한다.** 쓰기 후 서비스는 `MedicationAnalysisRefreshRequestedEvent` / `InactivityAnalysisRefreshRequestedEvent`를 발행하고, `AnalysisRefreshEventListener`가 **트랜잭션 커밋 이후** 분석을 실행한다. 관련 이벤트를 복호화해 상태를 계산하고, `ward_analysis_state`(`ward_id, analysis_type` unique)에 상태 코드만 upsert한다 — **민감 상세 값은 분석 테이블에 저장하지 않는다.** 복약 상태: `0=ON_TIME, 1=DELAYED, 2=MISSED`(`PENDING`은 저장 대상에서 제외). 미활동 상태: `0=NORMAL, 1=INACTIVE_*, 2=stale/unreliable/disconnected 계열`.
- **`*/source` API가 민감 조회 경로다**: `GET /api/wards/{wardId}/...-source`는 OpenBao에서 private key를 조회해 `encrypted_package`를 복호화하고, 복호화된 metadata가 저장된 `aad_json`과 일치하는지 검증한 뒤 payload DTO를 반환한다. 보호자 접근은 항상 `ACCEPTED` 상태의 `GuardianWard` 관계가 필요하다.

### 암호화 활성화 주의사항
`CommonCryptoService`는 `ONCARE_SECURITY_CRYPTO_ENABLED=true`(기본값 **false**)가 아니면 `IllegalStateException`을 던진다. 암호화를 끈 상태로도 앱은 기동되지만, `saveRequiredSourceEvent()`를 사용하는 쓰기 흐름(복약/위치 저장)은 실패한다. 로컬에서 암호화를 완전히 사용하려면 접근 가능한 OpenBao(`BAO_ADDR`, `BAO_TOKEN`, `BAO_KV_MOUNT`, unseal 상태)와 FFI DLL(`native/crypto_ffi.dll` 또는 `CRYPTO_FFI_LIBRARY` 경로)이 필요하다.

### 외부 연동
각 연동은 자체 `client` + `config`와 mock/비활성 모드를 가진다: Kakao Local(장소·주소 검색, 좌표→주소 역지오코딩 — 안전구역 등록 화면용), Firebase FCM + SOLAPI(알림), 그리고 별도의 NestJS **Graph RAG** 서버(`graphrag.base-url`, 약물 상호작용 분석). 암호화/분석 테이블의 DDL은 `src/main/resources/db/*.sql`에 있다.
