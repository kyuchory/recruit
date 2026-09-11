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

---

## 다른 환경(새 머신)에서 처음 받아 실행하기

### 0. 사전 설치 (없으면 각각 설치)

| 도구 | 버전 | 확인 명령 | 설치 (macOS / Homebrew 기준) |
|---|---|---|---|
| **Git** | 2.x | `git --version` | `brew install git` |
| **Docker Desktop** | 최신 | `docker info` | https://www.docker.com/products/docker-desktop 또는 `brew install --cask docker` (설치 후 Docker Desktop 앱을 한 번 실행) |
| **JDK 21** | 21.x | `/usr/libexec/java_home -v 21` | `brew install openjdk@21` (Linux: `sdk install java 21-tem` 등) |
| **Node.js** | 20 이상 (24 검증됨) | `node -v` | `brew install node` 또는 nvm |
| **npm** | Node 동봉 | `npm -v` | — |

> Gradle / Spring Boot / Next.js 는 별도 설치 불필요. Gradle 은 `./gradlew` 래퍼가
> 자동 내려받고, Java 21 은 Gradle 툴체인이 위 경로에서 자동 탐지한다.
> Windows 는 `gradlew.bat`, WSL2 + Docker Desktop 권장.

### 1. 클론

```bash
git clone https://github.com/kyuchory/recruit.git
cd recruit
```

### 2. 환경 변수 파일 생성

```bash
cp .env.example .env
```

- `.env` 는 `.gitignore` 에 포함되어 커밋되지 않는다. **실제 시크릿은 절대 커밋 금지.**
- 로컬 개발은 `.env.example` 기본값 그대로 동작한다 (외부 자격증명 불필요).
- 실제 연동이 필요할 때만 아래 값을 채운다:
  - `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — Google OAuth2 로그인
  - `AI_ENABLED=true` + `OPENAI_API_KEY` — LLM 텍스트 추출
  - `STORAGE_PROVIDER=s3` + `S3_BUCKET` / `S3_REGION` (+ AWS 자격증명 체인) — 이미지 S3 저장
  - 값을 비워 두면 각각 dev 대체 구현(시드 로그인 / 규칙 파서만 / 로컬 파일 저장)이 쓰인다.

### 3. 인프라 기동 (PostgreSQL + Redis)

```bash
docker compose up -d
# 상태 확인 (둘 다 healthy 여야 함)
docker compose ps
```

- 포트: PostgreSQL `5432`, Redis `6379`. 이미 사용 중이면 `.env` 의
  `POSTGRES_PORT` / `REDIS_PORT` 를 바꾸고 `SPRING_DATASOURCE_URL` /
  `SPRING_DATA_REDIS_PORT` 도 함께 조정한다.
- PostgreSQL 18 이미지는 데이터 디렉터리 레이아웃이 바뀌어 볼륨을
  `/var/lib/postgresql` 에 마운트한다 (compose 에 이미 반영됨).
- 스키마 초기화는 필요 없다. 백엔드가 뜰 때 Flyway 가 `V1`–`V5` 를 자동 적용한다.
  깨끗한 상태로 다시 시작하려면 `docker compose down -v` (볼륨 삭제) 후 `up -d`.

### 4. 백엔드 실행 (Spring Boot, 포트 8080)

```bash
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun
```

또는 jar 로:

```bash
cd backend
./gradlew assemble
"$(/usr/libexec/java_home -v 21)/bin/java" -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

- 첫 실행은 Gradle 배포판 + 의존성을 내려받아 수 분 걸릴 수 있다 (네트워크 필요).
- `PATH` 의 기본 `java` 가 21 이면 `JAVA_HOME=...` 접두어는 생략 가능하지만,
  Gradle 데몬이 다른 JDK 를 잡는 것을 막으려면 명시하는 편이 안전하다.
- 기동 로그에 `Started RecruitInboxBackendApplication` 이 뜨고
  `Flyway ... migrations` 가 5개 적용되면 정상.

### 5. 프론트엔드 실행 (Next.js, 포트 3000)

```bash
cd frontend
npm install
npm run dev
```

- `frontend/.env.local` 이 없으면 만들고 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`
  한 줄을 넣는다 (레포에는 커밋되지 않음). 백엔드 포트를 바꿨다면 여기도 맞춘다.
- 개발 환경에는 Google 자격증명이 없으므로 **시드 사용자로 자동 로그인**된다
  (프론트가 `X-Dev-User-Id` 헤더를 자동 전송).

### 6. 동작 확인

| 확인 | 명령 / URL | 기대 |
|---|---|---|
| 인프라 연결 | `curl localhost:8080/api/v1/health` | `{"postgres":"up","redis":"up","flywayMigrations":5,...}` |
| Actuator | `curl localhost:8080/actuator/health` | `{"status":"UP", ...}` |
| URL 저장 (202) | `curl -s -X POST localhost:8080/api/v1/links -H 'Content-Type: application/json' -H 'X-Dev-User-Id: 00000000-0000-0000-0000-000000000001' -H "Idempotency-Key: $(uuidgen)" -d '{"url":"https://careers.example.com/jobs/1"}'` | `202` + `{linkId, applicationId, extractionRunId, "QUEUED"}` |
| 지원 목록 | `curl -s localhost:8080/api/v1/applications -H 'X-Dev-User-Id: 00000000-0000-0000-0000-000000000001'` | `{"items":[...], ...}` |
| 웹 UI | http://localhost:3000/app | 지원현황 대시보드 |

### 7. 테스트

```bash
# 백엔드: Docker 데몬만 떠 있으면 된다 (compose 불필요).
# 통합 테스트가 Testcontainers 로 PostgreSQL 18 + Redis 8 을 자동 기동한다.
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test   # 52 tests

# 프론트엔드
cd frontend && npm run lint && npm run build
```

> 백엔드 통합 테스트는 `AbstractIntegrationTest` 를 상속해 Testcontainers 가 관리하는
> PostgreSQL/Redis 컨테이너를 쓴다. `docker compose up` 은 로컬 앱 실행(`bootRun`)에만 필요하다.

### 8. 자주 겪는 문제

| 증상 | 원인 / 해결 |
|---|---|
| `bootRun` 시 `Port 8080 was already in use` | 다른 백엔드/프로세스가 8080 점유. `lsof -iTCP:8080 -sTCP:LISTEN -n -P` 로 확인 후 종료, 또는 `.env` 의 `SERVER_PORT` 변경 |
| `docker compose up` 이 `Cannot connect to the Docker daemon` | Docker Desktop 앱이 실행 중이 아님. 앱을 켜고 `docker info` 가 성공할 때까지 대기 |
| `UnsupportedClassVersionError ... class file version 65.0` | Java 17 로 실행됨. `JAVA_HOME=$(/usr/libexec/java_home -v 21)` 를 붙여 실행 |
| Flyway `checksum mismatch` | 적용된 마이그레이션 파일을 수정함. 파일을 되돌리거나 `docker compose down -v` 로 DB 초기화 |
| 프론트에서 API 호출이 CORS 로 막힘 | 백엔드 `.env` 의 `APP_CORS_ALLOWED_ORIGINS` 에 프론트 origin(`http://localhost:3000`) 포함 확인 |
| `GET /api/v1/health` 는 되는데 `postgres: down` | `.env` 의 `SPRING_DATASOURCE_URL` 호스트/포트가 compose 와 불일치 |

---

## 킬 스위치 (장애 시에도 URL 저장·수동 입력·지원 관리는 계속 동작)

| env | 효과 |
|---|---|
| `PARSER_ENABLED=false` | 추출 워커 정지. URL 저장은 계속 202. |
| `AI_ENABLED=false` (기본) | 모델 호출 없음. 규칙 기반 HTML 파서만. |
| `NOTIFICATIONS_ENABLED=false` | 알림 디스패처 정지. 규칙·예약 저장은 계속. |
| `PARSER_ALLOW_PRIVATE_HOSTS=true` | (테스트 전용) SSRF IP 차단 완화. 운영에서 절대 사용 금지. |

## API 계약

- `contracts/openapi.yaml` — 구현된 REST 엔드포인트 스냅샷 (수기 유지).
- 프론트 타입은 `frontend/lib/types.ts` 에서 이 계약과 정합을 맞춘다.
