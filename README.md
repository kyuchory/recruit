# 채용공고 Inbox — MVP

로컬 개발 스택. 설계 기준은 **`recruit-inbox-mvp-design-v1.1.md` (v1.1)** 이다.
`technical-design.md` / `schema.sql` 은 v1.1 이 대체한 v1.0 산출물이라 스키마 기준으로 쓰지 않는다
(모델 무관한 구현 디테일만 참고).

```
recuruit/
├─ frontend/   Next.js 16 (App Router) + TypeScript + Tailwind
├─ backend/    Spring Boot 4.1 + Java 21 + Spring MVC/Security/Data JPA + Flyway
├─ docker-compose.yml   PostgreSQL 18 + Redis 8
├─ .env.example
└─ (설계 문서 3종)
```

## 사전 요구

- Docker Desktop
- JDK 21 (`/usr/libexec/java_home -v 21` 로 확인). Gradle 툴체인이 21 로 컴파일하므로 21 이 설치돼 있어야 한다.
- Node 20+ (개발은 24 로 확인)

## 실행

```bash
# 1) 인프라
cp .env.example .env
docker compose up -d          # postgres:5432, redis:6379

# 2) 백엔드 (Flyway V1 자동 적용, ddl-auto=validate)
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun
#  또는:  ./gradlew assemble && "$(/usr/libexec/java_home -v 21)/bin/java" -jar build/libs/backend-0.0.1-SNAPSHOT.jar

# 3) 프론트엔드
cd frontend
npm install
npm run dev                   # http://localhost:3000
```

## 헬스 체크

| URL | 기대 |
|---|---|
| `http://localhost:8080/api/v1/ping` | `{"status":"ok",...}` |
| `http://localhost:8080/api/v1/health` | `{"postgres":"up","redis":"up","flywayMigrations":1,...}` |
| `http://localhost:8080/actuator/health` | `{"status":"UP",...}` (db, redis 컴포넌트 UP) |
| `http://localhost:3000` | 백엔드 health 결과를 SSR 로 렌더 |

## 현재 상태 (Step 1–4 완료)

- Git 저장소 + frontend/backend 프로젝트 생성
- docker-compose: PostgreSQL + Redis (healthcheck 포함)
- Spring Boot ↔ PostgreSQL/Redis 연결, Spring Session(Redis)
- Flyway `V1__baseline.sql`: v1.1 §6 스키마 22개 테이블 적용

다음: JPA 엔티티/리포지토리 → Application CRUD API → 지원현황 화면 → URL 저장(202) 흐름.
