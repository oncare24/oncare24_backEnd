# 백엔드 변경사항 공유 문서

## 1. 전체 요약
복약 일정/기록과 위치/디바이스 상태가 `encrypted_activity_log`에 암호화된 원천 이벤트로 저장되도록 변경  
복약/미활동 분석은 저장된 암호화 원천 이벤트를 복호화해서 수행하고, 분석 상세 데이터는 DB에 저장하지 않고 `ward_analysis_state`에 최신 상태값만 저장  
회원가입 시 사용자별 ML-KEM 키가 생성되어 OpenBao에 저장
보호자 초대 수락 시 보호자용 KeyEnvelope가 생성되어 OpenBao에 저장  
앱/보호자는 분석 상태 API로 최신 상태를 조회하고, source 조회 API로 복호화된 원천 데이터를 확인  
암호화 흐름에는 OpenBao, Rust FFI, `native/crypto_ffi.dll` 또는 `CRYPTO_FFI_LIBRARY` 설정이 필요 
Swagger/OpenAPI에는 주요 API 설명과 Bearer 인증 스키마가 추가

## 2. 주요 변경사항

### 2.1 계정 생성 / 인증
- 회원가입 시 `MlKemKeyProvisionService`가 사용자별 ML-KEM 키쌍을 생성
- 생성된 키는 OpenBao의 `cap2/users/{userId}/mlkem` 경로에 저장
- 저장 값은 `algorithm`, `public_key_b64`, `private_key_b64`, `created_at`
- ELDER 가입 시 `InactivityRuleProvisionService`를 통해 기본 미활동 감지 규칙이 생성
- 관련 API:
  - `POST /api/auth/signup`
  - `POST /api/auth/login`
  - `POST /api/auth/reissue`
  - `POST /api/auth/logout`

### 2.2 보호자-피보호자 연동
- GUARDIAN이 ELDER 전화번호로 초대를 생성
- ELDER가 초대를 수락하면 `GuardianWard` 상태가 `ACCEPTED`로 변경
- 초대 수락 시 `KeyEnvelopeProvisionService.provisionForAcceptedGuardian()`가 호출되어 보호자용 KeyEnvelope를 생성
- 보호자 관계 검증은 `GuardianWardStatus.ACCEPTED` 기준으로 수행
- 관련 API:
  - `POST /api/invitations`
  - `GET /api/invitations/sent`
  - `GET /api/invitations/received`
  - `POST /api/invitations/{id}/accept`
  - `POST /api/invitations/{id}/reject`
  - `DELETE /api/invitations/{id}`
  - `GET /api/guardian/wards`

### 2.3 복약 일정 / 복약 기록
- 복약 일정과 복약 기록은 먼저 `medication_schedule`, `medication_log`에 메타데이터 row를 생성
- 실제 복약명, 예정 시간, 복약 시간, 허용 지연 시간 등 상세 값은 payload로 만들어 `encrypted_activity_log`에 암호화 저장
- `medication_schedule`, `medication_log`에는 `ward_id`, `schedule_id`, `is_active`, `encrypted_activity_log_id` 등 연결/조회용 값이 남음
- `encrypted_activity_log_id`를 통해 원본 row와 암호화 이벤트가 연결
- 관련 API:
  - `POST /api/medications/schedules`
  - `GET /api/medications/schedules`
  - `GET /api/medications/schedules/{scheduleId}`
  - `PUT /api/medications/schedules/{scheduleId}`
  - `DELETE /api/medications/schedules/{scheduleId}`
  - `POST /api/medications/logs`
  - `GET /api/wards/{wardId}/medication-schedules/source`
  - `GET /api/wards/{wardId}/medication-logs/source`

### 2.4 복약 분석
- `MedicationAnalysisService`는 `encrypted_activity_log`에 저장된 `MEDICATION_EVENT`를 복호화해서 분석함
- 복약 일정 payload와 복약 기록 payload를 기준으로 예정 시간, 허용 조기/지연 시간, 실제 복약 시간을 비교
- 분석 결과는 `ward_analysis_state`에 `AnalysisType.MEDICATION`으로 저장
- 상세 분석 메시지나 복약명 같은 민감 정보는 상태 테이블에 저장하지 않음
- 상태 코드:
  - `0 = ON_TIME`
  - `1 = DELAYED`
  - `2 = MISSED`
  - `PENDING`은 코드상 최신 상태 저장 대상에서 제외

### 2.5 미활동 분석
- `InactivityAnalysisService`는 위치 이벤트(`LOCATION_EVENT`)와 디바이스 이벤트(`DEVICE_EVENT`)를 복호화해서 분석
- 마지막 위치 보고 시간, 위치 정확도, 이동 거리, 디바이스 연결 상태가 판단 기준으로 사용
- 디바이스가 `DISCONNECTED`이면 미활동 분석은 disconnected 계열 상태로 처리
- 분석 결과는 `ward_analysis_state`에 `AnalysisType.INACTIVITY`로 저장
- 상태 코드:
  - `0 = NORMAL`
  - `1 = INACTIVE_WARNING / INACTIVE_DANGER`
  - `2 = STALE_LOCATION_WARNING / STALE_LOCATION_DANGER / LOCATION_UNRELIABLE / DEVICE_DISCONNECTED`

### 2.6 암호화 / 복호화 / OpenBao 연동
- `CommonCryptoService`는 암호화/복호화 흐름의 중심 서비스
- Rust FFI는 `JnaCryptoFfiClient`, `CryptoFfiNative`, `CryptoFfiLoader`를 통해 호출
- OpenBao는 키와 envelope 저장소로 사용
- DataKey:
  - 날짜별 데이터 암호화 키
  - OpenBao `cap2/data-keys/{keyId}`에 저장
- ML-KEM Key:
  - 사용자별 공개키/개인키
  - OpenBao `cap2/users/{userId}/mlkem`에 저장
- KeyEnvelope:
  - DataKey를 사용자 또는 보호자 키로 열 수 있게 만드는 envelope
  - OpenBao `cap2/key-envelopes/{keyId}/...`에 저장
- `encrypted_activity_log` 저장 값:
  - `ward_id`
  - `data_key_id`
  - `event_type`
  - `source_table`
  - `source_id`
  - `occurred_at`
  - `encrypted_package`
  - `aad_json`
- source 조회 API는 OpenBao에서 private key를 조회한 뒤 `encrypted_package`를 복호화하고, AAD metadata 검증 후 payload DTO로 응답

### 2.7 Swagger/OpenAPI 문서 보강
- Swagger 설명이 추가/보강된 API 그룹:
  - Auth
  - Invitation
  - Ward
  - MedicationSchedule
  - MedicationLog
  - MedicationSource
  - Location
  - LocationSource
  - AnalysisState
- `SwaggerConfig`에 Bearer JWT 인증 스키마 `BearerAuth`가 등록
- 인증 불필요 API:
  - `POST /api/auth/signup`
  - `POST /api/auth/login`
  - `POST /api/auth/reissue`
  - `/api/health/**`
  - Swagger/OpenAPI 경로
- 인증 필요 API:
  - 로그아웃
  - 보호자/피보호자 연동
  - 복약
  - 위치
  - 분석 상태
  - source 조회 API 전체

## 3. DB / Entity 변경사항

### ward_analysis_state
- 새로 추가된 최신 분석 상태 저장 테이블
- 주요 컬럼:
  - `ward_id`
  - `analysis_type`
  - `status_code`
  - `analyzed_at`
- `(ward_id, analysis_type)` unique 제약 존재
- 복약/미활동 분석 상세 내용은 저장하지 않고 최신 상태값만 저장

### encrypted_activity_log
- 암호화된 원천 이벤트 저장 테이블
- 주요 컬럼:
  - `ward_id`
  - `data_key_id`
  - `event_type`
  - `source_table`
  - `source_id`
  - `occurred_at`
  - `encrypted_package`
  - `aad_json`
- `encrypted_package`는 암호문
- `aad_json`, `source_table`, `source_id`, `event_type`은 복호화 검증과 원천 row 추적용 메타데이터
- 추가된 event type:
  - `MEDICATION_EVENT`
  - `LOCATION_EVENT`
  - `DEVICE_EVENT`

### medication_schedule
- `encrypted_activity_log_id`가 추가
- 복약명, 예정 시간, 허용 시간, 반복 유형, 요일 컬럼은 nullable로 변경
- 실제 상세 값은 암호화 payload로 `encrypted_activity_log`에 저장
- 평문으로 남는 값은 주로 `id`, `ward_id`, `is_active`, timestamp, `encrypted_activity_log_id`

### medication_log
- `encrypted_activity_log_id`가 추가
- `taken_at`, `medication_name`, `log_source`는 nullable로 변경
- 실제 복약 기록 상세 값은 암호화 payload로 `encrypted_activity_log`에 저장
- 평문으로 남는 값은 주로 `id`, `ward_id`, `schedule_id`, timestamp, `encrypted_activity_log_id`

### location / device 관련 테이블
- 코드 diff 기준으로 `LocationReport`, `DeviceStatus` Entity의 컬럼 변경은 없음
- 위치/디바이스 상세 원천 데이터는 `encrypted_activity_log`에 `LOCATION_EVENT`, `DEVICE_EVENT`로 저장
- 위치 보고 row와 디바이스 상태 row는 `source_table/source_id`로 암호화 이벤트와 연결

## 4. API 변경사항

| 기능 | Method | URL | 인증 필요 여부 | Request 주요 값 | Response 주요 값 | 설명 |
|---|---|---|---|---|---|---|
| 회원가입 | POST | `/api/auth/signup` | 아니오 | phone, password, name, role | userId, phone, name, role | 사용자 생성, ML-KEM 키 생성, ELDER 기본 미활동 규칙 생성 |
| 로그인 | POST | `/api/auth/login` | 아니오 | phone, password | accessToken, refreshToken, accessTokenExpiresIn | JWT 발급 |
| 토큰 재발급 | POST | `/api/auth/reissue` | 아니오 | refreshToken | accessToken, refreshToken | refresh token 검증 후 재발급 |
| 로그아웃 | POST | `/api/auth/logout` | 예 | Bearer Token | void | refresh token 삭제 |
| 초대 생성 | POST | `/api/invitations` | 예 | wardPhone, relationship | invitation 정보 | GUARDIAN이 ELDER 초대 |
| 보낸 초대 조회 | GET | `/api/invitations/sent` | 예 | 없음 | sent invitation 목록 | 보호자가 보낸 PENDING 초대 조회 |
| 받은 초대 조회 | GET | `/api/invitations/received` | 예 | 없음 | received invitation 목록 | 피보호자가 받은 PENDING 초대 조회 |
| 초대 수락 | POST | `/api/invitations/{id}/accept` | 예 | invitation id | invitation 정보 | ACCEPTED 전환, 보호자 KeyEnvelope 생성 |
| 초대 거절 | POST | `/api/invitations/{id}/reject` | 예 | invitation id | void | REJECTED 전환 |
| 초대 취소 | DELETE | `/api/invitations/{id}` | 예 | invitation id | void | PENDING 초대 삭제 |
| 보호자 ward 목록 | GET | `/api/guardian/wards` | 예 | 없음 | ward 목록 | ACCEPTED 피보호자 목록 조회 |
| 복약 일정 생성 | POST | `/api/medications/schedules` | 예 | wardId, medicationName, scheduledTime, scheduleType, dayOfWeek/daysOfWeek | scheduleId, scheduleIds 등 | 복약 일정 생성 및 암호화 이벤트 저장 |
| 복약 일정 목록 조회 | GET | `/api/medications/schedules?wardId=` | 예 | wardId | schedule 목록 | Entity 기반 일정 조회 |
| 복약 일정 상세 조회 | GET | `/api/medications/schedules/{scheduleId}` | 예 | scheduleId | schedule 정보 | 단건 일정 조회 |
| 복약 일정 수정 | PUT | `/api/medications/schedules/{scheduleId}` | 예 | medicationName, scheduledTime, allowed minutes, scheduleType, active | schedule 정보 | 수정 이벤트 암호화 저장 |
| 복약 일정 삭제 | DELETE | `/api/medications/schedules/{scheduleId}` | 예 | scheduleId | void | 실제 삭제가 아니라 비활성화 |
| 복약 기록 생성 | POST | `/api/medications/logs` | 예 | wardId, scheduleId, takenAt, medicationName, logSource | logId 등 | 복약 기록 생성 및 암호화 이벤트 저장 |
| 위치 보고 | POST | `/api/locations/reports` | 예 | latitude, longitude, accuracy, reportSource | stored, reportId, reportedAt | ELDER 위치 보고, accuracy 기준 초과 시 저장 안 함 |
| 마지막 위치 조회 | GET | `/api/locations/last?wardId=` | 예 | wardId | latitude, longitude, deviceState 등 | 보호자가 마지막 위치와 디바이스 상태 조회 |
| 분석 상태 조회 | GET | `/api/wards/{wardId}/analysis-state` | 예 | wardId | medication, inactivity | 최신 복약/미활동 상태 조회 |
| 복약 일정 원천 데이터 조회 | GET | `/api/wards/{wardId}/medication-schedules/source` | 예 | includeInactive | 복호화된 일정 목록 | encrypted_activity_log 복호화 |
| 복약 기록 원천 데이터 조회 | GET | `/api/wards/{wardId}/medication-logs/source` | 예 | date | 복호화된 기록 목록 | encrypted_activity_log 복호화 |
| 위치 원천 데이터 조회 | GET | `/api/wards/{wardId}/location-records/source` | 예 | from, to | 복호화된 위치 목록 | 기본 24시간 범위 |
| 디바이스 상태 원천 데이터 조회 | GET | `/api/wards/{wardId}/device-status-records/source` | 예 | from, to | 복호화된 디바이스 상태 목록 | 기본 24시간 범위 |

## 5. 복약 데이터 흐름

### 5.1 데이터 수집
- 피보호자 또는 보호자가 복약 일정을 등록
- 피보호자 또는 권한 있는 보호자가 복약 기록을 남김
- 앱에서 수집되는 주요 값:
  - `wardId`
  - `medicationName`
  - `scheduledTime`
  - `scheduleType`
  - `dayOfWeek` 또는 `daysOfWeek`
  - `allowedEarlyMinutes`
  - `allowedDelayMinutes`
  - `takenAt`
  - `logSource`

### 5.2 데이터 전송
- 복약 일정은 `POST /api/medications/schedules`로 전송
- 복약 기록은 `POST /api/medications/logs`로 전송
- 두 API 모두 JWT 인증이 필요
- 보호자가 `wardId`를 보낼 경우 서버에서 `GuardianWardStatus.ACCEPTED` 관계인지 검증
- ELDER 본인은 자기 자신의 `wardId`에 대해서만 접근 가능

### 5.3 데이터 저장
- `medication_schedule`에는 일정 row와 `ward_id`, `is_active`, `encrypted_activity_log_id` 같은 메타데이터가 저장
- `medication_log`에는 기록 row와 `ward_id`, `schedule_id`, `encrypted_activity_log_id` 같은 메타데이터가 저장
- 실제 복약명, 복약 예정 시간, 실제 복약 시간, 허용 시간 등 상세 데이터는 `encrypted_activity_log.encrypted_package`에 암호화 저장
- event type은 `MEDICATION_EVENT`로 저장
- `encrypted_activity_log_id`로 복약 row와 암호화 이벤트가 연결

### 5.4 데이터 분석
- `MedicationAnalysisService`가 `encrypted_activity_log`에서 복약 일정/기록 이벤트를 조회
- 조회한 이벤트는 ward private key와 Rust FFI를 통해 복호화
- 일정/기록 저장 후 `MedicationAnalysisRefreshRequestedEvent`가 발행
- `AnalysisRefreshEventListener`가 트랜잭션 커밋 이후 분석 갱신을 실행
- 분석 기준:
  - 예정 시간
  - 허용 조기 복약 시간
  - 허용 지연 시간
  - 실제 복약 시간
- 결과 상태는 `ON_TIME`, `DELAYED`, `MISSED`, `PENDING` 중 하나로 산출

### 5.5 분석 결과 저장
- 분석 상세 내용은 저장하지 않음
- 최신 상태값만 `ward_analysis_state`에 저장
- 복약 분석은 `AnalysisType.MEDICATION`으로 저장
- `status_code` 의미:
  - `0 = ON_TIME`
  - `1 = DELAYED`
  - `2 = MISSED`
- `PENDING`은 코드상 `ward_analysis_state` 저장 대상에서 제외

### 5.6 데이터 공유 / 조회
- 앱 또는 보호자는 `GET /api/wards/{wardId}/analysis-state`로 최신 복약 분석 상태를 조회
- 복호화된 복약 일정 원천 데이터는 `GET /api/wards/{wardId}/medication-schedules/source`로 조회
- 복호화된 복약 기록 원천 데이터는 `GET /api/wards/{wardId}/medication-logs/source`로 조회
- 보호자 조회 시 `ACCEPTED` 관계 검증이 필요
- source 조회 API는 복호화 과정을 거치므로 민감 API로 취급

## 6. 미활동 데이터 흐름

### 6.1 데이터 수집
- 피보호자 앱에서 위치 정보가 수집
- 위치 보고 시 디바이스 상태도 함께 갱신
- 디바이스 미보고 시간이 길어지면 배치에서 disconnected 상태로 변경
- 앱에서 수집되는 주요 값:
  - `latitude`
  - `longitude`
  - `accuracy`
  - `reportSource`
  - `reportedAt` 또는 서버 기록 시간
  - `deviceState`

### 6.2 데이터 전송
- 앱은 `POST /api/locations/reports`로 위치를 백엔드에 전송
- JWT 인증이 필요합니다.
- 위치 보고는 ELDER 본인만 가능합니다.
- `accuracy` 기준은 100m입니다.
- `accuracy > 100m`이면 서버는 저장하지 않고 `stored=false`를 반환

### 6.3 데이터 저장
- 위치 보고가 유효하면 `LocationReport` row가 저장
- 디바이스 상태는 `DeviceStatus` row를 생성하거나 갱신
- 위치 상세 값은 `encrypted_activity_log`에 `LOCATION_EVENT`로 암호화 저장
- 디바이스 상태 상세 값은 `encrypted_activity_log`에 `DEVICE_EVENT`로 암호화 저장
- 위치/디바이스 원천 row와 암호화 이벤트는 `source_table/source_id`로 연결
- 복약처럼 원본 Entity에 encrypted_activity_log_id 컬럼을 추가한 방식은 아님, 위치/디바이스는 encrypted_activity_log의 source_table/source_id로 원천 row와 연결

### 6.4 데이터 분석
- `InactivityAnalysisService`가 위치 이벤트와 디바이스 이벤트를 복호화해서 분석
- 판단 기준:
  - 마지막 위치 보고 시간
  - 위치 정확도
  - 이동 거리
  - 디바이스 연결 상태
  - 미활동 감지 규칙의 warning/danger 기준
- 위치 보고 후 `InactivityAnalysisRefreshRequestedEvent`가 발행
- 디바이스 disconnected 감지 후에도 `InactivityAnalysisRefreshRequestedEvent`가 발행
- `AnalysisRefreshEventListener`가 트랜잭션 커밋 이후 미활동 분석을 실행

### 6.5 분석 결과 저장
- 분석 상세 내용은 저장하지 않음
- 최신 상태값만 `ward_analysis_state`에 저장
- 미활동 분석은 `AnalysisType.INACTIVITY`로 저장
- `status_code` 의미:
  - `0 = ACTIVE 또는 NORMAL`
  - `1 = INACTIVE`
  - `2 = UNKNOWN 또는 stale/unreliable/disconnected 계열`

### 6.6 데이터 공유 / 조회
- 앱 또는 보호자는 `GET /api/wards/{wardId}/analysis-state`로 미활동 분석 상태를 조회
- 보호자는 `GET /api/locations/last?wardId={wardId}`로 마지막 위치와 디바이스 상태를 조회
- 복호화된 위치 원천 데이터는 `GET /api/wards/{wardId}/location-records/source`로 조회
- 복호화된 디바이스 상태 원천 데이터는 `GET /api/wards/{wardId}/device-status-records/source`로 조회
- 보호자 조회 시 `ACCEPTED` 관계 검증이 필요
- source 조회 API는 복호화 과정을 거치므로 민감 API

## 7. OpenBao 사용 방법

### 7.1 OpenBao가 필요한 이유
- 사용자별 ML-KEM 키를 저장하기 위해 필요
- 날짜별 DataKey를 저장하기 위해 필요
- 사용자/보호자별 KeyEnvelope를 저장하기 위해 필요
- 복호화 시 필요한 private key와 DataKey를 조회하기 위해 필요
- 일반 DB에는 키 원문을 저장하지 않기 위한 목적

### 7.2 OpenBao에 저장되는 주요 값

| 구분 | 저장 경로 예시 | 용도 |
|---|---|---|
| 사용자 ML-KEM 키 | `cap2/users/{userId}/mlkem` | 사용자별 public/private key 저장 |
| 날짜별 DataKey | `cap2/data-keys/{keyId}` | 원천 이벤트 암호화에 사용하는 날짜별 데이터 키 저장 |
| 사용자 KeyEnvelope | `cap2/key-envelopes/{keyId}/user-{userId}` | 사용자 소유자 기준 DataKey envelope 저장 |
| 보호자 KeyEnvelope | `cap2/key-envelopes/{keyId}/guardian-{guardianId}` | 보호자가 암호화 데이터에 접근할 수 있도록 envelope 저장 |

### 7.3 백엔드 실행 시 필요한 환경변수

| 환경변수 | 의미 | 예시 값 | 없을 때 발생할 수 있는 문제 |
|---|---|---|---|
| `ONCARE_SECURITY_CRYPTO_ENABLED` | 암호화 기능 활성화 여부 | `true` | 기본값은 `false`; 암호화 필수 저장 흐름에서 실패할 수 있음 |
| `BAO_ADDR` | OpenBao 서버 주소 | `http://127.0.0.1:8200` | 기본값 사용. 다른 PC/OpenBao 사용 시 연결 실패 가능 |
| `BAO_TOKEN` | OpenBao 접근 토큰 | `hvs...` | 키 저장/조회 시 `BAO_TOKEN is required` 오류 발생 |
| `BAO_KV_MOUNT` | OpenBao KV mount 이름 | `secret` | 기본값은 `secret`; 실제 mount와 다르면 키 조회/저장 실패 |
| `CRYPTO_FFI_LIBRARY` | Rust FFI DLL 경로 | `D:\...\crypto_ffi.dll` | 없으면 기본 `native/crypto_ffi.dll` 사용. 둘 다 없으면 FFI 로딩 실패 |

### 7.4 로컬 실행 순서
1. MySQL을 실행
2. Redis를 실행
3. OpenBao를 실행
4. OpenBao가 seal 상태라면 unseal을 진행
5. 백엔드에서 사용할 `BAO_TOKEN`을 준비
6. 환경변수를 설정합니다.
   - `ONCARE_SECURITY_CRYPTO_ENABLED=true`
   - `BAO_ADDR=http://127.0.0.1:8200`
   - `BAO_TOKEN=...`
   - `BAO_KV_MOUNT=secret`
7. `native/crypto_ffi.dll` 파일이 있는지 확인
8. DLL 위치가 기본 경로가 아니면 `CRYPTO_FFI_LIBRARY`를 지정
9. 백엔드를 실행
10. 회원가입 → 보호자 초대 수락 → 복약 등록 → 위치 보고 → 분석 상태/source 조회 흐름을 확인

### 7.5 OpenBao 관련 주의사항
- OpenBao가 실행되지 않으면 키 저장/조회가 필요한 암호화/복호화 기능이 실패
- `BAO_TOKEN`이 없거나 잘못되면 OpenBao KV 접근이 실패
- `CRYPTO_FFI_LIBRARY` 또는 `native/crypto_ffi.dll`이 없으면 Rust FFI 호출이 실패
- 같은 와이파이의 다른 PC에서 OpenBao에 접속하려면 `BAO_ADDR`을 OpenBao가 실행 중인 PC의 LAN IP와 포트로 맞춰야함
- 암호화 기능을 끄면 앱 기동 자체는 가능하지만, 코드상 `saveRequiredSourceEvent()`를 사용하는 복약/위치 저장 흐름은 실패할 수 있음
- ACCEPTED 보호자가 없는 상태에서 암호화 원천 이벤트 저장을 시도하면 실패할 수 있음
