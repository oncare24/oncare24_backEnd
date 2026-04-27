# 보살핌 (Oncare24) Backend

> 통합 스마트 케어 플랫폼 - LLM 기반 문진형 병원 추천과 Graph RAG 기반 약물 상호작용 분석을 통합하여, 고령자의 건강 관리 및 긴급 대응을 지원합니다.

## 🛠 Tech Stack

| Category | Stack |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.4.5 |
| Build | Gradle |
| Database | MySQL 8.0, Redis 7, Neo4j (예정) |
| Auth | Spring Security + JWT (jjwt) |
| Docs | Swagger UI (springdoc-openapi) |

## 📁 패키지 구조

```
com.oncare.oncare24
├── Oncare24Application.java
└── global/                  ← 모든 도메인이 공유하는 공통 모듈
    ├── common/              BaseTimeEntity (created_at, updated_at)
    ├── config/              JpaAuditingConfig, SecurityConfig, SwaggerConfig
    ├── controller/          HealthController (동작 확인용)
    ├── exception/           ErrorCode, CustomException, GlobalExceptionHandler
    └── response/            ApiResponse<T>
```

도메인 추가 시 `com.oncare.oncare24.{auth, user, hospital, ...}` 식으로 패키지를 만들고, 그 안에 `controller/service/repository/entity/dto`로 분리합니다.

## 🚀 로컬 실행

### 1. 환경변수 설정

```bash
cp .env.example .env
# 필요시 .env 값 수정
```

### 2. DB / Redis 컨테이너 띄우기

```bash
docker compose up -d
```

확인:
```bash
docker compose ps
docker exec -it oncare-mysql mysql -uoncare -poncare1234 -e "SHOW DATABASES;"
docker exec -it oncare-redis redis-cli ping
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

또는 IDE에서 `Oncare24Application` 직접 실행. (기본 프로파일: `dev`)

### 4. 동작 확인

```bash
# 헬스 체크
curl http://localhost:8080/api/health

# 응답 예시
# { "success": true, "data": { "status": "UP", "service": "oncare24", "timestamp": "..." } }

# 에러 응답 포맷 테스트
curl http://localhost:8080/api/health/error-test

# 응답 예시
# { "success": false, "error": { "code": "U001", "message": "사용자를 찾을 수 없습니다." } }
```

Swagger UI: http://localhost:8080/swagger-ui.html

## 📦 표준 응답 포맷

### 성공
```json
{ "success": true, "data": { ... } }
```

### 실패
```json
{ "success": false, "error": { "code": "U001", "message": "사용자를 찾을 수 없습니다." } }
```

### 사용 예시 (Controller)

```java
// 성공
return ApiResponse.success(userService.getUser(id));

// 실패는 throw new CustomException(...) 으로 던지면 GlobalExceptionHandler가 처리
throw new CustomException(ErrorCode.USER_NOT_FOUND);
```

## 🔢 Error Code 체계

| Prefix | 도메인 |
|---|---|
| C0xx | 공통 (Common) |
| A0xx | 인증/인가 (Auth) |
| U0xx | 사용자 (User) |
| G0xx | 보호자 연동 (Guardian) - 예정 |
| S0xx | 안전 구역 (SafeZone) - 예정 |
| L0xx | 위치/모니터링 (Location) - 예정 |
| M0xx | 복약 (Medication) - 예정 |
| H0xx | 병원/문진 (Hospital) - 예정 |
| N0xx | 알림 (Notification) - 예정 |

새 도메인을 만들 때 `ErrorCode` enum에 prefix 맞춰서 추가하세요.

## 📋 구현 단계 (Step별 진행)

- [x] **Step 1~3** - 인프라 + 환경 분리 + 공통 모듈 ← 현재
- [ ] **Step 4** - User 도메인 (Entity, Repository)
- [ ] **Step 5** - JWT Provider + Filter + 진짜 SecurityConfig
- [ ] **Step 6** - 인증 API (회원가입/로그인/로그아웃/refresh)
- [ ] **Step 7+** - 병원 추천 (LLM + NMC), 그 외 도메인들
