# 채용공고 Inbox — MVP

채용공고 링크를 지원현황 표로 만들고, 서류·NCS·코딩테스트·AI 역량검사·면접 등
각 절차의 일정을 사용자 확인 후 알려준다.

설계 기준은 **`recruit-inbox-mvp-design-v1.1.md` (v1.1)**. `technical-design.md` /
`schema.sql` 은 v1.1 이 대체한 v1.0 산출물이라 스키마 기준으로 쓰지 않는다
(모델 무관한 구현 디테일만 참고). 현재 DB의 실제 상태는
`backend/src/main/resources/db/migration/` 의 Flyway `V1`–`V5`.

```
recuruit/
├─ frontend/   Next.js 16 (App Router) + TypeScript + Tailwind + TanStack Query
├─ backend/    Spring Boot 4.1 + Java 21 + Spring MVC/Security/Data JPA + Flyway
│  └─ 패키지: auth user link capture application applicationevent
│             parser(+html) ai(+openai) notification storage common
├─ docker-compose.yml   PostgreSQL 18 + Redis 8
├─ contracts/openapi.yaml
├─ .env.example
└─ (설계 문서 3종)
```

## 사전 요구

- Docker Desktop
- JDK 21 (`/usr/libexec/java_home -v 21`). Gradle 툴체인이 21로 컴파일한다.
- Node 20+ (개발은 24로 확인)

## 실행

```bash
cp .env.example .env
docker compose up -d                     # postgres:5432, redis:6379

cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun
#  또는:  ./gradlew assemble && "$(/usr/libexec/java_home -v 21)/bin/java" -jar build/libs/backend-0.0.1-SNAPSHOT.jar

cd frontend
npm install
npm run dev                              # http://localhost:3000
```

- 개발 환경에는 Google 자격증명이 없으므로 **시드 사용자로 자동 로그인**된다
  (`X-Dev-User-Id` 헤더, 프론트가 자동 전송).
- `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` 를 채우면 `/oauth2/authorization/google`
  플로우가 활성화되고 세션(Redis) 인증으로 전환된다.

## 헬스 / 스모크

| URL | 기대 |
|---|---|
| `GET :8080/api/v1/health` | `{"postgres":"up","redis":"up","flywayMigrations":5,...}` |
| `GET :8080/actuator/health` | `{"status":"UP", ...}` (db, redis UP) |
| `POST :8080/api/v1/links` `{"url":"..."}` (헤더 `X-Dev-User-Id`, `Idempotency-Key`) | `202` |
| `GET :8080/api/v1/applications` | `{"items":[...], ...}` |
| `http://localhost:3000/app` | 지원현황 대시보드 |

## 킬 스위치 (장애 시에도 URL 저장·수동 입력·지원 관리는 계속 동작)

| env | 효과 |
|---|---|
| `PARSER_ENABLED=false` | 추출 워커 정지. URL 저장은 계속 202. |
| `AI_ENABLED=false` (기본) | 모델 호출 없음. 규칙 기반 HTML 파서만. |
| `NOTIFICATIONS_ENABLED=false` | 알림 디스패처 정지. 규칙·예약 저장은 계속. |

## 테스트

```bash
cd backend && ./gradlew test        # 52 tests, live PostgreSQL 필요 (docker compose up)
cd frontend && npm run lint && npm run build
```

Testcontainers 전환은 하드닝 항목(설계 Day 9). 현재 백엔드 테스트는 로컬 compose
PostgreSQL 을 사용한다.
