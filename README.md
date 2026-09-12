# 채용공고 Inbox — MVP

채용공고 링크를 지원현황 표로 만들고, 서류·NCS·코딩테스트·AI 역량검사·면접 등
각 절차의 일정을 사용자 확인 후 알려준다.

설계 기준은 **`recruit-inbox-mvp-design-v1.1.md` (v1.1)**이며 현재 구현 인수인계는
`PROJECT_CONTEXT.md`를 먼저 본다. API 계약은 `contracts/openapi.yaml`, DB 실행 원본은
`backend/src/main/resources/db/migration/`의 Flyway `V1`–`V8`이다. 사람이 읽는 현행 명세는
`docs/API_REFERENCE.md`와 `docs/DATABASE_SCHEMA.md`에 정리했다. 루트 `schema.sql`은 v1.0
설계 이력 보존용이며 현재 DB 생성에 사용하지 않는다.

```
recuruit/
├─ frontend/   Next.js 16 (App Router) + TypeScript + Tailwind + TanStack Query
├─ backend/    Spring Boot 4.1 + Java 21 + Spring MVC/Security/Data JPA + Flyway
│  └─ 패키지: auth user link capture application applicationevent
│             parser(+html) ai(+openai) notification storage common
├─ docker-compose.yml   PostgreSQL 18 + Redis 8
├─ contracts/openapi.yaml
├─ docs/API_REFERENCE.md
├─ docs/DATABASE_SCHEMA.md
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
  - `SPRING_PROFILES_ACTIVE=prod` + `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` — Google OAuth2 로그인
  - `SPRING_PROFILES_ACTIVE=prod,kakao` + `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` — Kakao OIDC 로그인도 활성화
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
- 스키마 초기화는 필요 없다. 백엔드가 뜰 때 Flyway 가 `V1`–`V8` 를 자동 적용한다.
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
  `Flyway ... migrations`가 V8까지 적용되면 정상.

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

### 5.1 공개 진입·로그인 흐름

- `/`는 로그인 없이 볼 수 있는 소개·URL 접수 화면이고 `/app/**`는 사용자 영역이다.
- 공개 화면에서 URL을 제출했는데 인증되지 않았다면 URL을 `sessionStorage`에 임시 보관하고 로그인으로 이동한다. 로그인 성공 후 `/app?resume=url`에서 한 번만 등록하며 실패 시 재시도를 위해 값을 유지한다.
- OAuth 시작은 `/api/v1/auth/start/{google|kakao}?returnTo=/내부경로`를 사용한다. 서버는 외부 URL을 거부하고 내부 경로만 세션에 저장한다.
- Google은 `prod` 프로필에서, Kakao는 `kakao` 프로필을 추가했을 때 등록된다. 미설정 공급자는 로그인 화면에서 비활성화된다.
- Kakao Developers에서 OIDC를 활성화하고 callback을 `{backend-origin}/login/oauth2/code/kakao`로 등록한 뒤에만 실제 로그인을 확인할 수 있다.

운영 환경에서는 `SPRING_PROFILES_ACTIVE=prod`를 지정하고
`GOOGLE_CLIENT_ID`와 `GOOGLE_CLIENT_SECRET`을 모두 제공해야 한다. `prod` 프로필은
개발용 사용자 헤더를 비활성화하며, 자격증명이 빠지면 백엔드는 기동에 실패한다.

### 6. 동작 확인

| 확인 | 명령 / URL | 기대 |
|---|---|---|
| 인프라 연결 | `curl localhost:8080/api/v1/health` | `{"postgres":"up","redis":"up","flywayMigrations":8,...}` |
| Actuator | `curl localhost:8080/actuator/health` | `{"status":"UP", ...}` |
| URL 저장 (202) | `curl -s -X POST localhost:8080/api/v1/links -H 'Content-Type: application/json' -H 'X-Dev-User-Id: 00000000-0000-0000-0000-000000000001' -H "Idempotency-Key: $(uuidgen)" -d '{"url":"https://careers.example.com/jobs/1"}'` | `202` + `{linkId, applicationId, extractionRunId, "QUEUED"}` |
| 지원 목록 | `curl -s localhost:8080/api/v1/applications -H 'X-Dev-User-Id: 00000000-0000-0000-0000-000000000001'` | `{"items":[...], ...}` |
| 웹 UI | http://localhost:3000/app | 지원현황 대시보드 |

### 6.1 URL 및 일정 편집

- 지원현황 표에는 URL을 별도 열로 표시하지 않는다.
- URL이 있는 회사명 또는 직무를 클릭하면 원본 공고가 새 탭으로 열린다. 내부 관리 화면은 관리 열의 `수정`을 사용한다.
- URL이 없는 지원 건의 회사·직무는 내부 수정 화면으로 이동한다.
- `직접 등록` 폼의 URL은 선택 항목이다. 입력하면 기존 `links` 행의 `original_url`, `normalized_url`, `url_hash`에 저장한다.
- 지원 상세에서는 원본 URL을 등록·수정할 수 있다. 전형 카드의 `수정`을 눌러 정확한 일시, 날짜만, 날짜 미정으로 변경할 수 있으며 서류 전형은 상시 채용/채용 시 마감도 지원한다.
- 확정된 일정 수정 시 기존 미발송 알림은 취소되고 일정은 미확정 상태가 된다. 변경값 저장 후 `이 일정으로 확인`을 다시 눌러야 새 알림이 예약된다.
- URL 수정은 참조할 원본 주소만 변경하며 공고 분석을 자동 재실행하지 않는다.
- 지원현황의 빠른 필터와 지원 상태 필터를 함께 적용할 수 있다. 정렬 메뉴를 사용하거나 서류·NCS·코테·1차·2차 표 머리글 옆의 `↑`·`↓`를 눌러 날짜 방향을 즉시 선택하며 일정 없는 지원은 마지막에 표시한다.
- 전형 날짜 옆 D-day는 이벤트의 IANA 시간대로 계산한다. `D-8` 이상은 초록, `D-4`~`D-7`은 노랑, `D-DAY`~`D-3`은 빨강, 이미 지난 `D+N`은 회색으로 표시하며 완료·취소 전형은 제외한다.

### 6.2 자기소개서 이력

- 지원 수정 화면 상단의 `자기소개서 작성 →` 링크로 `/app/applications/{id}/essays` 전용 작성 화면에 진입한다. 지원 수정 화면은 회사·직무·URL·상태와 전형 타임라인을 우선 표시한다.
- 자기소개서 전용 화면에서 공고별 문항을 순서대로 추가하고 현재 답변을 저장한다.
- INBOX의 `자기소개서` 컬럼은 문항 없음 `미작성`, 일부 작성 `작성중`, 모든 문항 완료 `작성됨`을 표시한다. 상태를 누르면 해당 지원의 자기소개서 작성 화면으로 이동한다.
- 문항 제한은 제한 없음, 공백 포함/제외 글자 수, UTF-8 바이트, 한글·비ASCII 2바이트 환산 중 하나를 선택한다. 화면은 네 계산값을 항상 함께 표시하고 선택 기준을 초과하면 경고한다.
- `현재 내용 버전 저장`은 먼저 최신 편집 내용을 저장한 뒤 질문·제한·답변과 계산값을 스냅샷으로 남긴다. 이력에서 과거 답변을 현재 초안으로 복원할 수 있다.
- 문항 삭제 시 해당 문항의 모든 버전도 함께 삭제된다. 모든 조회·수정은 인증 사용자의 지원 소유권으로 제한한다.

### 6.3 내 지원정보

- `/app/profile`의 기본 화면은 인적사항부터 자기소개서 소재까지 모든 분류를 이력서 형식의 한 문서로 보여준다. 상단 `전체`·분류 목차는 고정되며 분류를 누르면 해당 섹션으로 스크롤한다. 전체 검색·전체 정보 복사와 각 섹션의 추가·수정을 제공한다.
- 예를 들어 인적사항은 한글·한자·영문 이름, 연락처와 주소를, 경력은 회사·직무·직급·직전연봉·근무기간·퇴사 사유·성과를 각각 저장한다. 자격증은 자격증명·등급·발행기관·취득일·등록번호를 저장한다. 인적사항과 병역은 한 묶음, 학력·경력·프로젝트 등은 여러 이력을 등록할 수 있다.
- 전용 필드 값은 AES-256-GCM으로 암호화한다. `민감 정보` 표시는 목록의 모든 값을 기본적으로 가리는 UI 설정이다. 로컬 기본 프로필은 별도 설정 없이 개발 전용 고정 키를 사용하고, `prod` 프로필은 `PROFILE_ENCRYPTION_KEY`에 base64로 인코딩한 별도 32바이트 키가 반드시 필요하다. 운영 키를 잃거나 변경하면 기존 값을 복호화할 수 없다.
- 주민등록번호, 여권번호, 계좌번호, 비밀번호 등 고위험 정보는 보관 대상에서 제외한다. 첨부된 개인 정보는 자동으로 DB에 가져오지 않는다.

### 7. 테스트

```bash
# 백엔드: Docker 데몬만 떠 있으면 된다 (compose 불필요).
# 통합 테스트가 Testcontainers 로 PostgreSQL 18 + Redis 8 을 자동 기동한다.
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test   # 99 tests (2026-09-12)

# 프론트엔드
cd frontend && npm run lint && npx tsc --noEmit && npm run build
```

> 백엔드 통합 테스트는 `AbstractIntegrationTest` 를 상속해 Testcontainers 가 관리하는
> PostgreSQL/Redis 컨테이너를 쓴다. `docker compose up` 은 로컬 앱 실행(`bootRun`)에만 필요하다.

### 7.1 E2E (Playwright)

최소 happy path 1개: dev 로그인 → URL 저장 → 지원 생성 확인 → 이벤트 등록 →
일정 확인(confirm) → 알림 규칙 생성 → 알림 생성 확인. 백엔드(+DB/Redis)가 실제로
떠 있어야 하며, 프론트 dev 서버는 Playwright 가 자동으로 띄운다.

```bash
docker compose up -d
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun &
cd frontend
npx playwright install chromium   # 최초 1회
npm run test:e2e
```

다른 포트를 쓰면 `NEXT_PUBLIC_API_BASE_URL` / `E2E_API_URL` 을 함께 맞춘다.

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
