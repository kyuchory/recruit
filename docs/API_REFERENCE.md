# Recruit Inbox API 현행 명세

기준일: 2026-09-12
기계 판독 가능한 계약 원본: [`contracts/openapi.yaml`](../contracts/openapi.yaml)

이 문서는 현재 구현된 API를 빠르게 파악하기 위한 인수인계용 요약이다. 요청·응답 필드,
enum, validation을 변경할 때는 OpenAPI를 먼저 또는 같은 변경에서 수정한다.

## 공통 규칙

- Base path: `/api/v1`
- 사용자 데이터 API는 인증이 필요하며 모든 조회·변경은 현재 `owner_id` 범위로 제한한다.
- 로컬 개발에서는 `X-Dev-User-Id`를 사용할 수 있지만 `prod` 프로필에서는 비활성화된다.
- 세션 기반 변경 요청은 CSRF 보호를 적용한다. `GET /auth/csrf`로 토큰을 얻는다.
- 생성 요청 중 계약에 명시된 작업은 `Idempotency-Key`를 사용한다.
- 낙관적 잠금 변경은 body의 `expectedVersion`, 삭제는 `If-Match`를 사용한다.
- 오류 body는 공통 `Error` 스키마를 사용하며 validation 400, 미인증 401, 권한 403,
  미존재 404, version/idempotency 충돌 409 계열로 처리한다.

## Endpoint 목록

| 영역 | Method | Path | 현재 용도 |
|---|---|---|---|
| 상태 | GET | `/ping`, `/health` | 프로세스 및 PostgreSQL·Redis·Flyway 상태 |
| 인증 | GET | `/me` | 현재 사용자 |
| 인증 | GET | `/auth/csrf` | CSRF 토큰 |
| 인증 | GET | `/auth/providers` | 현재 활성화된 Google/Kakao 공급자 |
| 인증 | GET | `/auth/start/{provider}` | 검증된 내부 `returnTo`를 보관하고 OAuth 시작 |
| 인증 | POST | `/auth/logout` | 세션 로그아웃 |
| 요약 | GET | `/summary` | 오늘 일정·이번 주 코테·확인 필요 집계 |
| 설정 | GET/PATCH | `/settings` | timezone·이메일 수신 설정 |
| 링크 | POST | `/links` | URL 저장, Application/ExtractionRun 생성, 신규 202·중복 200 |
| 링크 | GET | `/links/{id}` | 링크·지원·최신 추출 결과 |
| 추출 | GET | `/extractions/{id}` | 비동기 분석 진행 상태 |
| 지원 | GET/POST | `/applications` | 목록 또는 저장된 Link 기반 지원 생성 |
| 지원 | POST | `/applications/manual` | URL 선택형 수동 지원 생성 |
| 지원 | GET/PATCH/DELETE | `/applications/{id}` | 지원 조회·수정·삭제 |
| 지원 | POST | `/applications/{id}/confirm` | 추출값과 선택 전형 확정 |
| 전형 | GET/POST | `/applications/{id}/events` | 지원별 전형 목록·추가 |
| 전형 | GET/PATCH/DELETE | `/events/{eventId}` | 일정·상태·결과 수정 또는 삭제 |
| 전형 | POST | `/events/{eventId}/confirm` | 일정 확정 및 알림 계획 생성 |
| 알림 규칙 | GET/POST | `/events/{eventId}/notification-rules` | 규칙 목록·추가 |
| 알림 규칙 | PATCH/DELETE | `/notification-rules/{id}` | 규칙 수정·삭제 |
| 알림 | GET | `/events/{eventId}/notifications` | 전형의 예약 알림 |
| 알림 | GET | `/notifications` | 앱 내 알림함 |
| 알림 | PATCH | `/notifications/{id}` | 읽음 등 상태 변경 |
| 업로드 | POST | `/uploads` | 업로드 슬롯 생성 |
| 업로드 | POST | `/uploads/{id}/content` | 로컬 저장소 콘텐츠 업로드 |
| 업로드 | POST | `/uploads/{id}/complete` | 업로드 완료 |
| 업로드 | GET/DELETE | `/uploads/{id}` | 업로드 조회·삭제 |
| 자기소개서 | GET/POST | `/applications/{id}/essay-questions` | 공고별 문항 목록·추가 |
| 자기소개서 | GET | `/essay-progress` | INBOX용 지원별 전체/완료 문항 수 단일 집계 |
| 자기소개서 | PATCH/DELETE | `/essay-questions/{id}` | 문항·현재 답변·완료 상태 수정 또는 삭제 |
| 자기소개서 | GET/POST | `/essay-questions/{id}/revisions` | 저장 이력 목록·현재 내용 스냅샷 |
| 자기소개서 | POST | `/essay-questions/{id}/revisions/{revisionId}/restore` | 과거 버전을 현재 초안으로 복원 |
| 지원정보 | GET/POST | `/profile-items` | 전체/분류별 조회·항목 추가 |
| 지원정보 | PATCH/DELETE | `/profile-items/{id}` | 항목 수정·삭제 |

## 이번 구현에서 중요해진 계약

### 지원과 원본 URL

- `ApplicationResponse.sourceUrl`은 소유한 `links.original_url`에서 제공한다.
- `POST /applications/manual`의 `sourceUrl`은 선택값이다.
- `PATCH /applications/{id}`로 URL을 등록·변경할 수 있으나 자동 재분석은 시작하지 않는다.
- URL은 HTTP/HTTPS, 최대 4,096자이며 정규화·사용자별 중복 규칙을 따른다.

### 일정 편집과 D-day

- `ScheduleKind`: `EXACT`, `DATE_ONLY`, `UNKNOWN`, 서류 전형 전용 `ROLLING`, `UNTIL_FILLED`.
- 일정 관련 필드를 변경하면 확정을 해제하고 `scheduleVersion`을 증가시키며 기존 미발송
  알림을 취소한다. 다시 확인해야 새 알림이 예약된다.
- `EventResponse.daysUntil`은 이벤트의 IANA `timezone`에서 계산한 달력 날짜 차이다.
  브라우저 시간대에 의존하지 않는다. 프론트는 `D-N`, `D-DAY`, `D+N`으로 표시한다.

### 자기소개서 상태

- 문항 상태는 `DRAFT` 또는 `COMPLETED`다.
- 제한 기준은 `NONE`, `CHARACTERS_WITH_SPACES`, `CHARACTERS_WITHOUT_SPACES`,
  `UTF8_BYTES`, `KOREAN_2_BYTES`다.
- `/essay-progress`는 문항이 하나 이상 있는 지원만 반환한다. 응답이 없는 지원은 INBOX에서
  `미작성`, `0 < completedCount < totalCount`는 `작성중`, 모두 완료면 `작성됨`이다.
- revision은 명시적으로 저장한 스냅샷이며 자동 저장 이력이 아니다.

### 내 지원정보

- `ProfileCategory`: `PERSONAL`, `MILITARY`, `EDUCATION`, `LANGUAGE`,
  `CERTIFICATION`, `CAREER`, `AWARD`, `ACTIVITY`, `SKILL`, `PROJECT`, `STORY`.
- `fields`는 분류별 허용 key만 받고 알 수 없는 key는 400으로 거부한다.
- 전용 필드, `valueText`, `details`는 서버에서 AES-256-GCM 암호화 후 저장하고 응답 시 복호화한다.
- `sensitive`는 UI 기본 가림 여부이며 암호화 적용 여부를 결정하는 값은 아니다.
- 운영에서는 base64 32바이트 `PROFILE_ENCRYPTION_KEY`가 필수다.

## 분류별 `fields` key

| 분류 | 허용 key |
|---|---|
| PERSONAL | `koreanName`, `hanjaName`, `englishName`, `birthDate`, `gender`, `nationality`, `postalCode`, `roadAddress`, `lotAddress`, `mobile`, `email`, `veteransStatus`, `disabilityStatus` |
| MILITARY | `serviceStatus`, `branch`, `specialty`, `rank`, `dischargeReason`, `startDate`, `endDate` |
| EDUCATION | `schoolType`, `schoolName`, `location`, `majorCategory`, `major`, `attendanceType`, `admissionDate`, `graduationDate`, `graduationStatus`, `gpa`, `gpaScale`, `credits`, `transfer`, `doubleMajor`, `minor`, `gradeSummary`, `details` |
| LANGUAGE | `language`, `testName`, `score`, `registrationNumber`, `testDate`, `acquiredDate`, `speakingLevel`, `issuer` |
| CERTIFICATION | `name`, `grade`, `issuer`, `acquiredDate`, `registrationNumber` |
| CAREER | `company`, `department`, `employmentType`, `jobTitle`, `position`, `annualSalary`, `location`, `startDate`, `endDate`, `leaveReason`, `projects`, `achievements`, `summary` |
| AWARD | `name`, `prize`, `issuer`, `awardDate`, `role`, `summary`, `details` |
| ACTIVITY | `activityType`, `name`, `organization`, `role`, `startDate`, `endDate`, `hours`, `summary`, `details` |
| SKILL | `skillCategory`, `name`, `level`, `duration`, `notes` |
| PROJECT | `name`, `summary`, `startDate`, `endDate`, `role`, `techStack`, `links`, `achievements`, `details` |
| STORY | `title`, `theme`, `situation`, `task`, `action`, `result`, `lesson`, `keywords` |

## 변경 체크리스트

API를 변경할 때 함께 확인한다.

1. Controller/DTO/service validation
2. `contracts/openapi.yaml` path와 schema
3. `frontend/lib/types.ts`
4. 이 문서의 endpoint·행동 규칙
5. API 통합 테스트와 프론트 typecheck
