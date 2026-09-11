# Recruit Inbox Project Context

## Product
한국 취준생용 채용공고/지원 일정 관리 서비스.

핵심 플로우:
URL 저장
→ Link/Application/ExtractionRun 생성
→ 202
→ 비동기 공고 분석
→ 사용자 확인
→ ApplicationEvent
→ NotificationRule
→ Notification

## Source of truth

우선순위:

1. recruit-inbox-mvp-design-v1.1.md
2. technical-design.md
3. schema.sql

충돌 시 v1.1 우선.

## Architecture

Frontend:
- Next.js 16
- TypeScript
- Tailwind
- TanStack Query/Table

Backend:
- Spring Boot 4.1
- Java 21
- Spring MVC
- Spring Security
- JPA
- Flyway

Infra:
- PostgreSQL 18
- Redis 8

MVP는 Spring monolith.
RabbitMQ 사용하지 않음.
PostgreSQL이 async job source of truth.

## Important domain rules

- ApplicationEvent 중심으로 채용 절차 관리
- AI 결과는 suggestion
- 사용자 수정값을 AI가 overwrite하면 안 됨
- 알림은 ApplicationEvent 기준
- 사용자가 확인한 일정만 알림 예약
- DATE_ONLY에 임의 시간 생성 금지
- URL 저장 요청은 분석을 기다리지 않고 202 반환

## Current implementation

Step 1~26 구현 완료.

Backend:
- Application CRUD
- ApplicationEvent CRUD
- URL import
- ExtractionRun
- DB-backed parser worker
- HTML parser
- SSRF protection
- AI adapter
- upload/storage
- confirmation flow
- notification rules/planner/dispatcher
- Google OAuth/session
- settings
- summary

Frontend:
- /login
- /app
- /app/applications/[id]
- /app/notifications
- /app/settings

Tests:
- backend 78 tests passing
- frontend lint/typecheck/build passing
- Playwright happy path passing

## Current priorities

1. 실제 한국 채용공고 parser benchmark
2. parser 정확도 개선
3. 그 결과를 보고 OpenAI Text fallback 활성화
4. 이후 Vision
5. 실제 Email/S3 provider 연결

## Do not

- architecture를 임의로 microservice로 변경하지 말 것
- RabbitMQ 추가하지 말 것
- Redis를 durable queue로 사용하지 말 것
- AI 결과로 user-confirmed 값을 덮어쓰지 말 것
- 실제 credential/API key를 코드에 commit하지 말 것
