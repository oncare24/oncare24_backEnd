# 변경 이력 (CHANGELOG)

> 이 문서는 **시간순 코드 변경 이력**을 기록한다. 최신 항목이 맨 위.
> 아키텍처/암호화 흐름 명세는 `change_summary.md`, 프로젝트 가이드는 `CLAUDE.md`를 본다.
>
> **작성 규칙**: 코드를 수정/추가/삭제하면 작업 단위로 항목을 추가한다. 각 항목은
> `날짜 · 제목` 헤더 아래에 **무엇을/왜/영향 범위(파일·API·설정)**를 적는다. 날짜는 절대값(YYYY-MM-DD).

---

## 2026-07-01 · 봉지 편집 API 신설 — packet 추가 / 이름 변경 (MANUAL)

### 무엇을 / 왜
편집 전면 봉지화를 위해 기존 봉지 API에 없던 두 기능을 추가. 프론트 편집이 평면 CRUD 없이
봉지 API만으로 동작하도록 하는 백엔드 기반(편집 전면 봉지화 1단계 — 백엔드).

### 신규 API
- `POST /api/wards/{wardId}/medication-schedules/groups/{groupId}/packets` — 봉지에 시각 추가.
  약명은 group의 기존 약명을 상속, 요일별 row 생성. **MANUAL만**(AUTO는 M005).
- `PATCH /api/wards/{wardId}/medication-schedules/groups/{groupId}` `{medicationName}` — 봉지 이름 변경.
  group의 모든 활성 성분 이름 갱신(시각/요일/기간 유지). **MANUAL만**(AUTO는 M005).

### 편집 정책
- AUTO(CODEF 자동) 봉지는 시각 이동(4-3)/삭제(4-6)만 허용, **시각 추가·이름 변경은 잠금**.
- `ErrorCode.MEDICATION_GROUP_NOT_EDITABLE`(M005, 400) 추가.

### 영향 범위
- `MedicationGroupCommandService`(`addPacket`/`renameGroup` + `groupRows`/`assertManual` 헬퍼),
  `MedicationGroupController`(POST packets, PATCH group), `UpdateGroupNameRequest` 신규, `ErrorCode` M005.
- 기존 API/동작 변경 없음(추가만).

### 검증
- `./gradlew compileJava` 통과.
- `MedicationGroupCommandServiceTest`에 addPacket(MANUAL 성공/AUTO 거부), renameGroup 케이스 추가 — 통과.

---

## 2026-07-01 · CODEF 자동등록 중복 방지 — codefKey null 대체 키(fallback)

### 무엇을 / 왜
처방번호/약품코드가 없는 약은 `CodefKeyHasher.hash()`가 null을 반환 →
`autoRegisterFromPrescriptions`가 중복 검사를 스킵해 재분석마다 중복 등록되던 버그.
봉지 모델 도입 목적(제안서 1장의 두 근본 원인: 시각 잔존 + 중복) 중 남은 "중복"을 해소.

### 변경
- `CodefKeyHasher.hashFallback(drugName, manufactureDate, dailyDoses)` 추가 — 처방번호/약품코드가
  없을 때 약명+조제일+1일횟수 기반 안정적 대체 키(HMAC-SHA256). 재분석 시 동일 입력이면 같은 키가 되어
  중복 차단. 정상 키와 입력 형식이 달라 충돌하지 않음. `hash()`는 공통 `hmacHex()`로 리팩터.
- `MedicationScheduleService.autoRegisterFromPrescriptions`: `codefKeyBidx`가 null이면 `hashFallback`으로
  대체해, 항상 `existsByWardIdAndCodefKeyBidx` 중복 검사를 수행.

### 영향 범위
- `CodefKeyHasher`, `MedicationScheduleService` (medication 도메인). API/스키마/설정 변경 없음.

### 검증
- `./gradlew compileJava` 통과.
- `CodefKeyHasherTest`(신규), `MedicationScheduleServiceTest`(autoRegister dedup 케이스 추가),
  `MedicationGroupCommandServiceTest` 통과.

---

## 2026-06-30 · 복약 일정 봉지(DoseGroup) 모델 도입 (4장 API 계약)

### 무엇을 / 왜
자동(CODEF)·수동 등록의 정체성(identity) 불일치로 생기는 시각 수정 잔존·중복 버그를 구조적으로
없애기 위해 봉지(groupId)를 1급 식별자로 도입. 모든 봉지 CRUD를 groupId 기준으로 수행(이름+시각
추측 제거). 제안서의 옵션 A(평면 row에 group_id/source 컬럼 추가) 채택.

### 데이터 모델
- `MedicationSchedule`: `group_id`(VARCHAR 100), `source`(AUTO/MANUAL) 평문 컬럼 + `(ward_id, group_id)` 인덱스. `updateScheduledTime()`, `assignGroup()` 추가.
- `MedicationSchedulePayload`: `groupId`, `source` 필드 추가(하위호환 생성자 유지 → 기존 암호화 데이터는 null로 복호화).
- `MedicationSource` enum 신규.

### 등록 로직 (groupId 부여)
- 수동 `create`: `group_id="manual:<uuid>"`, `source=MANUAL`.
- `update`: 기존 group_id/source 보존(요일 재구성 시 동일 groupId 전파).
- `autoRegisterFromPrescriptions`: `group_id="codef:rx:<prescribeNo>"`(처방 단위), 처방번호 없으면 `"codef:gen:<uuid>"`. `source=AUTO`. codefKey null이어도 groupId 항상 발급.

### API (4장 계약 그대로 — 경로·필드명)
- `GET /api/wards/{wardId}/medication-schedules/source` (4-1) — 봉지 계층 `{groups:[{groupId,source,medicationName,packets:[{scheduledTime,label,scheduleType,daysOfWeek,startDate,endDate,active,items:[{scheduleId,name}]}]}]}`. **기존 List 응답을 교체**.
- `GET .../medication-schedules/today?date=` (4-2, 신규) — `{slots:[{scheduledTime,label,allTaken,items:[{scheduleId,name,taken,takenAt}]}]}`. 성분별 복용 상태는 평문 `medication_log`(schedule_id/taken_at) 매칭(복호화 불필요).
- `PUT .../groups/{groupId}/packets/time` (4-3) `{fromTime,toTime}` — 봉지 시각 일괄 이동.
- `PUT .../groups/{groupId}/packets/{scheduledTime}` (4-4) `{scheduleType,daysOfWeek,startDate,endDate}`.
- `POST .../groups` (4-5) `{medicationName,packets[]}` — 수동 봉지 생성(MANUAL 고정).
- `DELETE .../groups/{groupId}` 및 `.../groups/{groupId}/packets/{scheduledTime}` (4-6).

### 영향 범위
- 신규: `MedicationGroupController`, `MedicationGroupQueryService`, `MedicationGroupCommandService`, `MedicationSource`, 응답/요청 DTO 11종.
- 변경: `MedicationSchedule`, `MedicationSchedulePayload`, `MedicationScheduleSourceResponse`, `MedicationScheduleService`(create/update/autoRegister), `MedicationSourceQueryService`(groupId/source 매핑·legacy fallback), `MedicationSourceQueryController`(schedules/source 이전, logs/source 유지), `MedicationScheduleRepository`(group 조회 메서드).
- 공존 유지: 기존 수동 CRUD `/api/medications/schedules`, 로그 조회 `/medication-logs/source`.
- DDL: `db/medication_schedule_group_ddl.sql`(group_id/source 컬럼 + 인덱스 + 백필 전략 주석). 백필 미실행 — 조회 시 group_id null이면 `"legacy:<scheduleId>"`로 노출되어 백필 없이도 동작.

### 검증
- `./gradlew compileJava` 통과.
- 테스트 소스셋은 본 작업 이전부터 광범위하게 깨져 있어(`AuthServiceTest`, `InactivityAnalysisServiceTest` 등 무관 도메인 + Create/Update 요청 record 시그니처 불일치) 전체 `test` 실행은 보류 — 제안서 7장의 "기존 깨진 테스트 별개 정리" 대상. 본 작업으로 메서드가 이전된 `MedicationSourceQueryControllerTest`의 schedules 케이스만 정리.

### 미결 / 후속
- codefKey null 약의 **처방 단위 중복 차단 휴리스틱** 미구현(groupId는 항상 발급해 4-1/CRUD는 동작하나 재등록 차단은 codefKeyBidx 존재 시에만).
- 평문 `group_id`/`source` 백필 배치(암호화 payload 복호화 필요).
- 옵션 B(DoseGroup/DoseItem 정규화)는 후순위.

---

## 2026-06-30 · LLM 문진·길안내 기능 제거

작업 브랜치: `chore/remove-hospital-navigation` (아직 커밋 전)

### 배경 / 이유
LLM 기반 문진형 병원 추천과 길안내(도보·대중교통) 기능을 제품에서 제외하기로 함.
두 도메인 모두 외부 API 호출 결과만 반환하고 **DB 엔티티·테이블이 없어** 데이터 마이그레이션 없이 제거 가능.
다른 도메인이 두 패키지를 import하는 곳은 0건이라 의존성 충돌 없음.

### 삭제
- **`hospital/` 패키지 전체** — LLM 문진·병원 추천
  - 외부 연동: OpenAI(`gpt-4o-mini`), NMC 국립중앙의료원(병원 검색, XML)
  - 제거된 API: `POST /api/hospitals/recommend`, `POST /api/medical-chat/turn`
- **`navigation/` 패키지 전체** — 길안내
  - 외부 연동: TMAP(도보), ODsay(대중교통)
  - 제거된 API: `POST /api/navigation/walking`, `POST /api/navigation/transit`
- **`kakao/dto/RegionCode.java`** 및 `KakaoLocalClient.coord2region()` / `normalizeSigungu()`
  - hospital의 `RealNmcApiClient`만 쓰던 죽은 코드 (NMC 검색 범위 좁히기용)
- **`ErrorCode`** 의 길안내 전용 코드 `V001`(`NAVIGATION_FAILED`), `V002`(`NO_TRANSIT_ROUTE`)

### 설정 정리
- `application.yml` — `openai.*`, `nmc.*`, `tmap.*`, `odsay.*` 블록 제거
- `.env` / `.env.example` — `OPENAI_*`, `NMC_*`, `TMAP_*`, `ODSAY_*` 키 제거
- `build.gradle` — NMC XML 파싱 전용 의존성 `com.fasterxml.jackson.dataformat:jackson-dataformat-xml` 제거
- 스테일 주석 정리: `ErrorCode`의 H0xx(병원/문진) 항목, `KakaoRestClientConfig`의 NMC/OpenAI 언급

### 유지
- **`kakao` 도메인 유지** — `GET /api/kakao/search`, `GET /api/kakao/coord2address`가
  안전구역 등록/수정 화면에서 계속 사용됨. `KAKAO_REST_API_KEY`도 유지.

### 검증
- 메인 코드 컴파일 `./gradlew compileJava` **성공** (깨진 참조 0건)
- 참고: `MedicationScheduleServiceTest`는 `CreateMedicationScheduleRequest` 생성자 시그니처
  불일치로 컴파일 실패하나, **이번 변경과 무관한 기존 문제**(main에서도 동일하게 실패). 별도 처리 필요.
