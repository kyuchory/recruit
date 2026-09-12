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

제품·구현 문서 우선순위:

1. recruit-inbox-mvp-design-v1.1.md
2. technical-design.md
3. PROJECT_CONTEXT.md

API 계약 원본은 `contracts/openapi.yaml`, DB 실행 원본은 Flyway `V1`–`V8`이다.
`docs/API_REFERENCE.md`와 `docs/DATABASE_SCHEMA.md`는 각각의 사람이 읽는 요약본이다.
루트 `schema.sql`은 v1.0 설계 이력일 뿐 현행 스키마가 아니다. 제품 결정 충돌 시 v1.1을 우선한다.

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
- 일정 날짜/시간을 수정하면 확인을 해제하고 기존 미발송 알림을 취소한 뒤 재확정한다
- 공고 URL은 Link의 original_url에 보관하며 지원 목록/상세 응답에는 sourceUrl로 제공한다
- 지원 건의 URL 수정은 원본 참조 링크만 바꾸며 자동 재분석을 시작하지 않는다

## Current implementation

Step 1~26 구현 완료.

Backend:
- Application CRUD
- ApplicationEvent CRUD 및 일정 방식/날짜/시간 수정
- URL import, 수동 지원의 선택 URL 저장, 기존 지원 URL 수정
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
- career profile item CRUD with production field encryption
- summary
- application essay question/revision CRUD and owner-scoped progress aggregation

Frontend:
- 공통 디자인 시스템: 로고, 대표 브랜드 컬러(`--brand` 등 CSS 변수 4개, `app/globals.css`), Pretendard 폰트, `lg`(1024px) 미만은 반응형, 그 이상은 고정 폭(1152px) 레이아웃(창을 좁혀도 컬럼이 눌리지 않고 필요 시 가로 스크롤)
- 공용 컴포넌트: `useConfirm()` 확인 모달(`components/confirm-dialog.tsx`, 브라우저 기본 confirm 대체), 범용 `Modal`(`components/modal.tsx`), `AppNav`(활성 탭 표시)
- /login
- / (공개 랜딩: 로고 헤더, URL 접수 폼, 기능 배너 2종)
- /app (URL 열은 숨김, 회사·직무에서 원본 공고 새 탭 열기, 관리 열의 수정 링크, 지원 상태/전형 일정을 표에서 바로 인라인 수정, 리스트/캘린더 뷰 전환, 직접 등록은 모달)
- /app/jobs (채용공고 목록·채용달력 — 사람인 API 응답 스키마와 동일한 구조의 목데이터 120건, 실제 API 연동 전 화면 검증용. `lib/saramin-mock-data.ts`→`lib/job-postings.ts`(정규화)→화면 이 순서로 의존하며 실제 연동 시 `saramin-mock-data.ts`만 교체하면 됨. 상세 모달의 주요업무/자격요건/우대사항/복지 등은 사람인 API가 주지 않는 필드라 `lib/job-posting-content.ts`에 별도 템플릿으로 분리. "내 리스트에 추가"는 실제 `POST /api/v1/applications/manual` 호출)
- /app/applications/[id] (지원 정보·원본 URL 수정 및 전형 타임라인 관리)
- /app/applications/[id]/essays (지원별 자기소개서 문항·답변·버전 이력)
- /app/notifications
- /app/settings
- /app/profile (전체 이력서 기본 보기, 분류 목차 스크롤 + 스크롤 위치 기반 자동 탭 전환, 개인 지원정보 보관·검색·복사)

Tests:
- backend 99 tests passing (2026-09-12 전체 실행, 오늘 백엔드 코드 변경 없어 재실행 생략)
- frontend lint/typecheck/build passing (2026-09-13)
- Playwright happy path passing

## Resume checkpoint

다음 작업자는 아래 순서로 현재 상태를 복구한다.

1. 이 문서의 `Recent changes`와 `Current priorities`를 읽는다.
2. REST 변경 전 `contracts/openapi.yaml`과 `docs/API_REFERENCE.md`를 함께 갱신한다.
3. DB 변경은 기존 migration 수정이 아니라 새 Flyway migration으로 추가하고 `docs/DATABASE_SCHEMA.md`의 migration history와 테이블 명세를 갱신한다.
4. 개발 환경은 고정 profile 암호화 키를 쓰지만 운영은 별도 `PROFILE_ENCRYPTION_KEY`가 없으면 기동하지 않는다. 실제 키는 문서·소스·예제 env에 기록하지 않는다.
5. 사람인 API access key는 발급받았으나 실제 연동은 아직이다. `/app/jobs`는 동일 스키마의 목데이터로 화면만 먼저 구현했고, live 연동 전 응답 형태·쿼터·오류 정책을 재검증한다.
6. Kakao는 REST API 키·Client Secret을 로컬 `.env`에 넣고 `SPRING_PROFILES_ACTIVE=prod,kakao`로 OIDC discovery까지 확인했다(별도 포트 인스턴스로 검증, 상시 구동 중인 개발 서버는 미교체). 다음 작업자는 메인 개발 서버를 이 프로필로 재기동해 실제 로그인 왕복까지 검증한다. 실제 키 값은 `.env`(gitignore)에만 있고 문서·커밋에는 없다.
7. 완료 전 백엔드 test/build와 프론트 lint/typecheck/build를 모두 실행한다.

## Recent changes (2026-09-13)

- 프론트엔드 공통 디자인 시스템을 도입했다: 로고(`public/logo.png`), 대표 브랜드 컬러를 `app/globals.css`의 CSS 변수 4개(`--brand`/`--brand-dark`/`--brand-light`/`--brand-foreground`)로 상수화, Pretendard 폰트, `Button`에 `variant="brand"` 추가. 전 페이지(랜딩/로그인/대시보드/프로필/알림함/설정/지원상세/자소서)에 일괄 적용했다.
- `/app/**` 공통 레이아웃 헤더를 로고+활성 탭 표시 네비게이션(`components/app-nav.tsx`)으로 교체했다. 헤더와 본문 컨테이너는 `lg`(1024px) 미만에서만 반응형으로 줄어들고, 그 이상에서는 1152px 고정 폭이라 창을 좁혀도 컬럼이 눌리지 않고 필요 시 페이지가 가로 스크롤된다.
- 브라우저 기본 `window.confirm()`을 전부 커스텀 확인 모달(`useConfirm()`, `components/confirm-dialog.tsx`)로 교체했다. 범용 `Modal`(`components/modal.tsx`)도 추가해 대시보드 "직접 등록" 폼을 인라인 확장 대신 모달로 전환했다.
- 대시보드(`/app`): 상태 배지를 인라인 드롭다운으로, 전형 일정 칸을 클릭하면 그 자리에서 날짜/시간을 만들거나 수정할 수 있게 했다(값이 없으면 생성 POST, 있으면 수정 PATCH). 리스트/캘린더 뷰 토글과 월간 캘린더(`components/schedule-calendar.tsx`, 이벤트를 날짜별로 표시)를 추가했다. 요약 3칸과 필터 칩의 중복 정보를 필터 칩에 개수 배지를 붙이는 방식으로 통합해 압축했다.
- `/app/profile` 스크롤스파이를 추가해 수동 클릭 없이 스크롤 위치에 따라 분류 탭이 자동 전환되도록 했다. 이 과정에서 발견한, 이 페이지의 sticky 탭바가 상단 공통 헤더를 가리던 버그도 함께 고쳤다.
- **`/app/jobs`(채용공고) 신규**: 사람인 `/job-search` 응답과 동일한 필드 구조의 목데이터 120건(`lib/saramin-mock-data.ts`) → 정규화 계층(`lib/job-postings.ts`) → 화면 순으로 의존하게 만들어, 실제 연동 시 목데이터 파일만 교체하면 되게 했다. 상세 필터(채용형태/기업형태/직무/지역/마감제외/검색), 리스트+페이지네이션, 채용달력(`components/job-calendar.tsx`, 마감일 기준) 뷰 전환을 제공한다. 사람인 API가 주지 않는 상세 콘텐츠(주요업무/자격요건/우대사항/복지, 조회·스크랩수)는 별도 템플릿 레이어(`lib/job-posting-content.ts`)로 분리했다. "내 리스트에 추가"는 실제 `POST /api/v1/applications/manual`을 호출해 개인 지원 리스트(Application)로 편입한다 — 이 목데이터는 개인 지원 리스트와 완전히 분리된 별도 데이터이며 섞이지 않는다.
- 헤더 네비게이션에 "채용공고" 탭을 추가했다.
- 백엔드/DB/REST 계약 변경 없음 — 오늘 작업은 전부 프론트엔드였다.

## Recent changes (2026-09-12)

- Flyway V7–V8과 `/app/profile` 내 지원정보 보관함을 추가했다. 인적사항·병역·학력·외국어·자격증·경력·수상·활동·기술·프로젝트·자소서 소재를 분류별 전용 필드로 관리한다.
- `/app/profile`은 전체 분류를 이력서 형식으로 기본 표시하고, 상단 분류 목차 클릭 시 해당 섹션으로 스크롤한다. 섹션별 추가·수정과 전체 검색·전체 복사를 제공한다.
- 자격증 전용 필드는 자격증명·등급·발행기관·취득일·등록번호를 지원한다.
- 지원현황 일정 컬럼의 정렬 조작은 테두리·배경 없는 작은 세로형 `▲`·`▼` 버튼으로 제공하며 선택 상태를 시각적으로 강조하지 않는다. 컬럼명을 클릭해도 같은 컬럼의 오름차순·내림차순이 토글된다.
- 지원현황의 각 전형 날짜 옆에 D-day를 표시한다. 8일 이상은 초록, 4–7일은 노랑, 3일 이내·당일은 빨강, 경과한 `D+N` 일정은 회색이며 완료·취소 전형은 제외한다.
- 자기소개서는 지원 수정 화면에서 `/app/applications/[id]/essays`로 분리했다. 수정 화면 상단에 작은 진입 링크를 두고 회사·URL·상태 다음에 전형 타임라인을 우선 표시한다.
- 지원현황에 자기소개서 컬럼을 두고 `미작성`·`작성중`·`작성됨` 상태와 완료 문항 수를 표시한다. 상태 클릭 시 해당 지원의 자기소개서 화면으로 이동하며 진행률은 사용자 범위 단일 집계 API로 조회한다.
- 전용 필드 값과 기존 범용 값은 AES-256-GCM으로 암호화하고, 소유자 범위 API·낙관적 잠금을 적용한다. 로컬은 개발 전용 고정 키를 기본 사용하고 운영에서는 별도 `PROFILE_ENCRYPTION_KEY`가 필수다. 민감 표시 항목은 화면에서 기본 가림 처리하며 고위험 식별정보 저장 금지를 안내한다.
- Flyway V6와 `essay` 도메인을 추가했다. 지원별 자기소개서 문항·현재 초안·작성 상태와 명시적으로 저장한 버전 이력을 보관한다.
- 지원 상세에서 문항 추가/수정/삭제, 답변 저장, 완료 표시, 버전 생성·조회·복원을 제공한다. 공백 포함/제외 글자 수, UTF-8 바이트, 한글 2바이트 환산을 실시간 표시한다.
- 지원현황에 정확한 지원 상태 필터와 서류·NCS·코테·1차·2차 일정별 날짜 정렬을 추가했다. 정렬 선택창을 유지하면서 각 표 머리글 옆의 위·아래 화살표로 방향을 즉시 선택할 수 있고, 일정 없는 행은 마지막에 둔다.
- `/`를 공개 소개·URL 접수 화면으로 바꾸고 `/app/**`에 현재 사용자 확인 기반 인증 가드를 추가했다.
- 비로그인 URL 접수는 sessionStorage에 임시 보관하고 OAuth 로그인 뒤 원래 경로로 복귀하여 한 번만 등록한다. 등록 실패 시 값은 삭제하지 않는다.
- OAuth 시작 endpoint가 검증된 내부 `returnTo`를 세션에 보관한다. 성공 handler는 registration id로 GOOGLE/KAKAO identity를 구분하며 외부 redirect 값은 허용하지 않는다.
- 로그인 공급자 조회 endpoint와 Kakao OIDC 선택 프로필(`prod,kakao`)을 추가했다. 자격증명이 없으면 Kakao는 비활성화되므로 기존 로컬/Google 기동을 막지 않는다.
- 사람인 채용공고 API는 아직 연동하지 않았다. 키 없이 계약·adapter·fixture·UI를 개발할 수 있으나 live 응답/쿼터/오류 검증과 운영 활성화는 access key 발급 후 수행한다.
- 전형 카드에 일정 수정 UI를 추가했다. EXACT, DATE_ONLY, UNKNOWN 및 서류의 ROLLING/UNTIL_FILLED 전환을 지원한다.
- 일정 종류 전환 시 서로 양립할 수 없는 이전 날짜 필드를 서버에서 제거한다.
- 확정된 일정을 수정하거나 날짜 미정으로 바꾸면 schedule_version을 증가시키고 상태를 UNSCHEDULED로 되돌리며 기존 예약을 취소한다.
- Application 응답에 sourceUrl을 추가했다. URL import로 저장한 값과 수동 등록/수정한 값 모두 Link.original_url에서 조회한다.
- 지원현황 표에는 URL을 별도 열로 노출하지 않는다. URL이 있으면 회사·직무가 원본 공고를 새 탭으로 열고, 관리 열의 `수정`이 내부 상세 화면으로 이동한다.
- 수동 지원 등록 폼과 지원 상세 폼에서도 선택적으로 URL을 저장·수정할 수 있다.
- URL은 http/https만 허용하고 최대 4,096자, 정규화 및 사용자별 중복 방지 규칙을 적용한다.
- URL 수정은 기존 Link 행을 갱신하며 새 ExtractionRun을 만들지 않는다. 최신 내용 재분석은 별도 재분석 흐름의 책임이다.
- DB 컬럼 추가 없이 기존 links.original_url/normalized_url/url_hash를 사용하므로 migration은 없다.
- 검증: `./gradlew test`, `./gradlew build`, `npm run lint`, `npx tsc --noEmit`, `npm run build` 통과.

## Current priorities

1. 사람인 access key로 실제 `/job-search` 연동 — `lib/saramin-mock-data.ts`를 실제 API 호출로 교체하고 `/app/jobs`를 그 위에서 재검증 (목데이터 기반 화면은 완성)
2. 실제 한국 채용공고 parser benchmark와 정확도 개선
3. parser 결과를 바탕으로 OpenAI Text fallback 활성화, 이후 Vision 검토
4. 메인 개발 서버를 `prod,kakao` 프로필로 재기동해 Kakao 실제 로그인 왕복 검증 (자격증명 확보·OIDC discovery 검증까지는 완료)
5. 실제 Email/S3 provider 연결 — 알림 인프라는 이미 있으나 발송 채널이 로그 스텁이라 현재 사용자에게 실제 알림이 가지 않음

## Do not

- architecture를 임의로 microservice로 변경하지 말 것
- RabbitMQ 추가하지 말 것
- Redis를 durable queue로 사용하지 말 것
- AI 결과로 user-confirmed 값을 덮어쓰지 말 것
- 실제 credential/API key를 코드에 commit하지 말 것
