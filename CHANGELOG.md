# 변경 이력 (CHANGELOG)

> 이 문서는 **시간순 코드 변경 이력**을 기록한다. 최신 항목이 맨 위.
> 아키텍처/암호화 흐름 명세는 `change_summary.md`, 프로젝트 가이드는 `CLAUDE.md`를 본다.
>
> **작성 규칙**: 코드를 수정/추가/삭제하면 작업 단위로 항목을 추가한다. 각 항목은
> `날짜 · 제목` 헤더 아래에 **무엇을/왜/영향 범위(파일·API·설정)**를 적는다. 날짜는 절대값(YYYY-MM-DD).

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
