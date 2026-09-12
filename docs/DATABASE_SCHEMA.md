# Recruit Inbox 데이터베이스 현행 명세

기준일: 2026-09-12
DBMS: PostgreSQL 18
실행 가능한 원본: [`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration)

Flyway migration이 유일한 실행 원본이다. 이미 적용된 migration은 수정하지 않고 다음 버전 파일을
추가한다. 루트 `schema.sql`은 v1.0 설계 이력이라 현재 DB에 적용하지 않는다.

## Migration history

| 버전 | 파일 | 내용 |
|---|---|---|
| V1 | `V1__baseline.sql` | 사용자·인증, 링크·추출, 지원·전형, 알림, 업로드/기기, 사용량·삭제 작업 기본 스키마 |
| V2 | `V2__relax_jpa_version_check.sql` | users, links, applications, application_events의 JPA 초기 version 0 허용 |
| V3 | `V3__relax_extraction_run_version_check.sql` | extraction_runs version 0 허용 |
| V4 | `V4__relax_capture_asset_version_check.sql` | capture_assets version 0 허용 |
| V5 | `V5__relax_notification_version_checks.sql` | notification_rules, notifications version 0 허용 |
| V6 | `V6__application_essays.sql` | 지원별 자기소개서 문항과 명시적 revision 이력 |
| V7 | `V7__career_profile_items.sql` | 사용자별 재사용 지원정보 항목 |
| V8 | `V8__career_profile_structured_fields.sql` | 분류별 구조화 값을 암호문으로 저장하는 `field_values` 추가 |

현재 fresh database의 Flyway 목표 버전은 **8**이다.

## 전체 테이블 카탈로그

| 영역 | 테이블 | 역할 |
|---|---|---|
| 사용자/인증 | `users` | 사용자, timezone, 알림 설정, 상태 |
| 사용자/인증 | `auth_identities` | Google/Kakao/Email 외부 identity |
| 사용자/인증 | `password_credentials`, `auth_action_tokens` | 이메일 인증 확장용 자격증명·일회 토큰 |
| 수집 | `links` | 원본·정규화 URL과 사용자별 hash |
| 수집 | `capture_assets` | 업로드 파일 메타데이터와 보관 상태 |
| 추출 | `extraction_runs`, `extraction_run_assets` | 비동기 분석 작업과 입력 asset 연결 |
| 추출 | `public_parse_cache` | 공개 페이지 파싱 캐시 |
| 비용 | `usage_budgets`, `usage_ledger` | 사용자별 분석/AI 사용량 예산·원장 |
| API | `api_idempotency` | 멱등 요청 응답 재현 |
| 지원 | `applications` | 회사·직무·지원 상태와 사용자 확정 데이터 |
| 지원 | `application_events` | 서류/NCS/코테/면접 등 전형 일정 |
| 자기소개서 | `application_essay_questions` | 지원별 질문·현재 답변·완료 상태 |
| 자기소개서 | `application_essay_revisions` | 명시적으로 저장한 질문/답변 스냅샷 |
| 지원정보 | `career_profile_items` | 인적·병역·학력·경력 등 재사용 정보 |
| 알림 | `notification_rules` | 이벤트별 상대/달력 알림 규칙 |
| 알림 | `notifications` | 일정 version별 예약·앱 내 알림 |
| 알림 | `notification_deliveries` | 채널별 발송 작업과 재시도 |
| 기기 | `user_devices`, `push_subscriptions` | 향후 push 기기와 구독 |
| 설정 | `notification_channel_preferences` | 사용자별 채널 허용 여부 |
| 운영 | `provider_events` | 외부 provider webhook 멱등 처리 |
| 운영 | `deletion_jobs` | 계정 삭제 작업 상태 |

## V6 자기소개서 스키마

### `application_essay_questions`

| 컬럼 | 타입/제약 | 의미 |
|---|---|---|
| `id` | uuid PK | 문항 ID |
| `owner_id` | uuid NOT NULL | 소유자 경계 |
| `application_id` | uuid NOT NULL, `(application_id, owner_id)` FK, cascade | 대상 지원 |
| `sort_order` | int, 0 이상 | 표시 순서 |
| `question_text` | text, 1~5,000자 | 질문 |
| `limit_type` | varchar(32) | NONE/공백 포함·제외/UTF-8 byte/한글 2-byte |
| `limit_value` | int nullable | NONE이면 null, 나머지는 1~1,000,000 |
| `answer_text` | text, 최대 100,000자 | 현재 초안 |
| `status` | varchar(16) | `DRAFT` 또는 `COMPLETED` |
| `created_at`, `updated_at` | timestamptz | 생성·수정 시각 |
| `version` | bigint, 0 이상 | 낙관적 잠금 |

인덱스: `(application_id, sort_order, id)`. 문항 삭제 시 revision도 cascade 삭제된다.

### `application_essay_revisions`

문항의 `question_text`, 제한값, `answer_text`, 네 가지 계산 결과를 revision 번호와 함께 보존한다.
`UNIQUE(question_id, revision_no)`이며 `(question_id, revision_no DESC)` 인덱스를 사용한다.
지원 또는 문항 삭제 시 cascade 삭제된다.

## V7–V8 지원정보 스키마

### `career_profile_items`

| 컬럼 | 타입/제약 | 의미 |
|---|---|---|
| `id` | uuid PK | 항목 ID |
| `owner_id` | uuid FK users, cascade | 사용자 경계 |
| `category` | varchar(24) CHECK | PERSONAL, MILITARY, EDUCATION, LANGUAGE, CERTIFICATION, CAREER, AWARD, ACTIVITY, SKILL, PROJECT, STORY |
| `label` | varchar(200), trim 1~200자 | 목록 표시명. 일부 분류는 전용 필드에서 자동 파생 |
| `value_text` | text, 최대 30,000자 | AES-256-GCM 암호문 |
| `details` | text, 최대 140,000자 | AES-256-GCM 암호문 |
| `field_values` | text, 최대 140,000자 | 구조화 JSON 전체의 AES-256-GCM 암호문 |
| `started_on`, `ended_on` | date nullable | 범용 기간, 종료일은 시작일 이상 |
| `sensitive` | boolean | UI에서 기본 가림 여부 |
| `sort_order` | int, 0 이상 | 분류 내 표시 순서 |
| `created_at`, `updated_at` | timestamptz | 생성·수정 시각 |
| `version` | bigint, 0 이상 | 낙관적 잠금 |

인덱스: `(owner_id, category, sort_order, id)`. 분류별 허용 field key는
[`API_REFERENCE.md`](API_REFERENCE.md#분류별-fields-key)에 기록한다.

## 관계와 삭제 규칙

```text
users
 ├─ links ─ applications ─ application_events ─ notification_rules ─ notifications
 │                    └─ application_essay_questions ─ application_essay_revisions
 └─ career_profile_items
```

- 사용자 데이터는 `owner_id`를 함께 FK에 포함해 다른 사용자의 하위 행을 연결할 수 없게 한다.
- application 삭제 시 events, essay questions/revisions가 cascade 삭제된다.
- 일정이 수정되면 행을 교체하지 않고 `schedule_version`을 올린다. 기존 미발송 notification은
  취소하며 재확정 시 현재 version으로 다시 계획한다.
- URL 수정은 기존 `links` 행의 `original_url`, `normalized_url`, `url_hash`를 갱신한다.
  이번 기능에는 별도 URL migration이 필요하지 않았다.
- `EventResponse.daysUntil`은 계산 필드이므로 DB 컬럼이 아니다.
- 자기소개서 진행 상태 역시 질문 집계 결과이므로 별도 DB 컬럼이 아니다.

## 암호화와 키 운영

- `career_profile_items.value_text`, `details`, `field_values`의 평문은 DB에 저장하지 않는다.
- 알고리즘은 AES-256-GCM이며 운영 키는 base64 인코딩된 정확히 32바이트 값이다.
- 로컬 기본 profile에만 개발 전용 고정 키가 있다. `prod`는 `PROFILE_ENCRYPTION_KEY`가 없거나
  잘못되면 기동/저장을 허용하지 않는다.
- 운영 키 유실 또는 무계획 교체 시 기존 데이터를 복호화할 수 없다. 실제 키는 Git, 로그,
  문서, `.env.example`에 기록하지 않고 secret manager에 보관한다.
- 키 회전 기능은 아직 없다. 회전이 필요하면 구키/신키를 함께 읽는 migration 절차를 먼저 설계한다.

## DB 변경 체크리스트

1. 이미 적용된 migration을 수정하지 않고 다음 `V{N+1}__*.sql` 추가
2. owner 경계 FK, cascade 범위, CHECK, index 검토
3. JPA entity/enum/DTO와 migration 정합성 확인
4. 이 문서의 migration history·테이블 명세 갱신
5. `contracts/openapi.yaml`에 노출 계약 변화 반영
6. Testcontainers 통합 테스트와 `./gradlew build` 실행
