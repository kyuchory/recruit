-- ============================================================================
-- Recruit Inbox MVP -- Flyway baseline (V1)
-- Model: recruit-inbox-mvp-design-v1.1.md section 6 (the v1.1 redesign).
-- NOTE: The bundled schema.sql / technical-design.md describe the superseded
--       v1.0 model and are intentionally NOT the basis for this migration.
-- PostgreSQL 16+. gen_random_uuid() is in core since PG13.
--
-- Conventions (v1.1 section 6.1):
--   * business tables: id uuid PK, created_at/updated_at timestamptz default now(),
--     version bigint default 1 check(version > 0)
--   * exceptions (composite PK, no id/version): usage_budgets, api_idempotency,
--     provider_events, public_parse_cache (also no owner), extraction_run_assets
--   * personal parents carry UNIQUE(id, owner_id); children use (parent_id, owner_id)
--     composite FKs so cross-owner links are impossible.
--   * timestamps stored UTC; local dates as `date`; timezone as IANA id.
-- ============================================================================
BEGIN;

-- ---------------------------------------------------------------------------
-- 6.2 users, auth, common links, capture, extraction, cost/idempotency
-- ---------------------------------------------------------------------------
CREATE TABLE users (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email                 varchar(320),
  email_verified_at     timestamptz,
  timezone              varchar(64)  NOT NULL DEFAULT 'Asia/Seoul',
  email_enabled         boolean      NOT NULL DEFAULT false,
  email_consent_at      timestamptz,
  email_suppressed_at   timestamptz,
  state                 varchar(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE','DELETING')),
  deletion_requested_at timestamptz,
  created_at            timestamptz  NOT NULL DEFAULT now(),
  updated_at            timestamptz  NOT NULL DEFAULT now(),
  version               bigint       NOT NULL DEFAULT 1 CHECK (version > 0)
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE auth_identities (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider         varchar(16) NOT NULL CHECK (provider IN ('GOOGLE','KAKAO','EMAIL')),
  provider_subject varchar(255) NOT NULL,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  version          bigint      NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (provider, provider_subject)
);
CREATE INDEX idx_auth_identities_owner ON auth_identities(owner_id);

CREATE TABLE password_credentials (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id            uuid        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  password_hash       text        NOT NULL,
  password_changed_at timestamptz NOT NULL DEFAULT now(),
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  version             bigint      NOT NULL DEFAULT 1 CHECK (version > 0)
);

CREATE TABLE auth_action_tokens (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id   uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash char(64)    NOT NULL UNIQUE,
  purpose    varchar(20) NOT NULL CHECK (purpose IN ('EMAIL_VERIFY','PASSWORD_RESET')),
  expires_at timestamptz NOT NULL,
  used_at    timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version    bigint      NOT NULL DEFAULT 1 CHECK (version > 0)
);

CREATE TABLE links (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id              uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  original_url          text,
  normalized_url        text,
  url_hash              char(64),
  kind                  varchar(16) NOT NULL DEFAULT 'job',
  title                 varchar(500),
  source_channel        varchar(24) NOT NULL,
  extraction_generation bigint      NOT NULL DEFAULT 0,
  archived_at           timestamptz,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  version               bigint      NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (id, owner_id)
);
CREATE UNIQUE INDEX uq_links_owner_urlhash ON links(owner_id, url_hash) WHERE url_hash IS NOT NULL;
CREATE INDEX idx_links_owner_created ON links(owner_id, created_at DESC, id);

CREATE TABLE capture_assets (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id     uuid        NOT NULL,
  link_id      uuid        NOT NULL,
  kind         varchar(8)  NOT NULL CHECK (kind IN ('TEXT','IMAGE')),
  storage_key  text,
  text_content text,
  sha256       char(64),
  mime         varchar(100),
  bytes        bigint CHECK (bytes IS NULL OR bytes >= 0),
  state        varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','READY','REJECTED','DELETED')),
  expires_at   timestamptz NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  version      bigint      NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (id, owner_id, link_id),
  FOREIGN KEY (link_id, owner_id) REFERENCES links(id, owner_id) ON DELETE CASCADE,
  CHECK (
    (kind = 'TEXT'  AND text_content IS NOT NULL AND storage_key IS NULL) OR
    (kind = 'IMAGE' AND storage_key  IS NOT NULL AND text_content IS NULL)
  )
);
CREATE INDEX idx_capture_assets_expiry ON capture_assets(expires_at);

CREATE TABLE extraction_runs (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id             uuid        NOT NULL,
  link_id              uuid        NOT NULL,
  generation           bigint      NOT NULL,
  source_kind          varchar(24) NOT NULL,
  status               varchar(16) NOT NULL DEFAULT 'QUEUED'
                         CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','NEEDS_INPUT','FAILED','CANCELLED')),
  progress_stage       varchar(32),
  schema_version       varchar(32) NOT NULL,
  parser_version       varchar(64) NOT NULL,
  model_config_version varchar(64) NOT NULL,
  model_id             text,
  source_hash          char(64),
  result               jsonb       NOT NULL DEFAULT '{}',
  warnings             jsonb       NOT NULL DEFAULT '[]',
  attempt_count        int         NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  next_attempt_at      timestamptz NOT NULL DEFAULT now(),
  lease_token          uuid,
  lease_until          timestamptz,
  error_code           varchar(64),
  started_at           timestamptz,
  finished_at          timestamptz,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  version              bigint      NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (id, owner_id, link_id),
  UNIQUE (link_id, generation),
  FOREIGN KEY (link_id, owner_id) REFERENCES links(id, owner_id) ON DELETE CASCADE,
  CHECK ((lease_token IS NULL) = (lease_until IS NULL))
);
CREATE UNIQUE INDEX uq_extraction_runs_active ON extraction_runs(link_id) WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX idx_extraction_runs_due   ON extraction_runs(next_attempt_at, id) WHERE status = 'QUEUED';
CREATE INDEX idx_extraction_runs_lease ON extraction_runs(lease_until)         WHERE status = 'RUNNING';

CREATE TABLE extraction_run_assets (
  run_id     uuid NOT NULL,
  asset_id   uuid NOT NULL,
  owner_id   uuid NOT NULL,
  link_id    uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (run_id, asset_id),
  FOREIGN KEY (run_id, owner_id, link_id)   REFERENCES extraction_runs(id, owner_id, link_id) ON DELETE CASCADE,
  FOREIGN KEY (asset_id, owner_id, link_id) REFERENCES capture_assets(id, owner_id, link_id)  ON DELETE CASCADE
);

CREATE TABLE public_parse_cache (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  url_hash             char(64)    NOT NULL,
  parser_version       varchar(64) NOT NULL,
  schema_version       varchar(64) NOT NULL,
  model_config_version varchar(64) NOT NULL,
  content_hash         char(64)    NOT NULL,
  result               jsonb       NOT NULL,
  fetched_at           timestamptz NOT NULL,
  expires_at           timestamptz NOT NULL,
  etag                 text,
  last_modified        text,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  UNIQUE (url_hash, parser_version, schema_version, model_config_version)
);
CREATE INDEX idx_public_parse_cache_expiry ON public_parse_cache(expires_at);

CREATE TABLE usage_budgets (
  scope_key      varchar(100)  NOT NULL,
  period_start   date          NOT NULL,
  limit_usd      numeric(14,6) NOT NULL DEFAULT 0 CHECK (limit_usd >= 0),
  reserved_usd   numeric(14,6) NOT NULL DEFAULT 0 CHECK (reserved_usd >= 0),
  spent_usd      numeric(14,6) NOT NULL DEFAULT 0 CHECK (spent_usd >= 0),
  limit_calls    int           NOT NULL DEFAULT 0 CHECK (limit_calls >= 0),
  reserved_calls int           NOT NULL DEFAULT 0 CHECK (reserved_calls >= 0),
  used_calls     int           NOT NULL DEFAULT 0 CHECK (used_calls >= 0),
  created_at     timestamptz   NOT NULL DEFAULT now(),
  updated_at     timestamptz   NOT NULL DEFAULT now(),
  PRIMARY KEY (scope_key, period_start)
);

CREATE TABLE usage_ledger (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id        uuid          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  run_id          uuid          NOT NULL REFERENCES extraction_runs(id) ON DELETE CASCADE,
  stage           varchar(16)   NOT NULL,
  reservation_key varchar(200)  NOT NULL UNIQUE,
  input_tokens    int           NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
  output_tokens   int           NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
  image_units     int           NOT NULL DEFAULT 0 CHECK (image_units >= 0),
  estimated_usd   numeric(14,6) NOT NULL,
  actual_usd      numeric(14,6),
  status          varchar(16)   NOT NULL CHECK (status IN ('RESERVED','SETTLED','RELEASED','UNKNOWN')),
  created_at      timestamptz   NOT NULL DEFAULT now(),
  updated_at      timestamptz   NOT NULL DEFAULT now(),
  version         bigint        NOT NULL DEFAULT 1 CHECK (version > 0)
);

CREATE TABLE api_idempotency (
  owner_id        uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  key             varchar(128) NOT NULL,
  request_hash    char(64)     NOT NULL,
  response_status int,
  response_json   jsonb,
  expires_at      timestamptz  NOT NULL,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  updated_at      timestamptz  NOT NULL DEFAULT now(),
  PRIMARY KEY (owner_id, key)
);
CREATE INDEX idx_api_idempotency_expiry ON api_idempotency(expires_at);

-- ---------------------------------------------------------------------------
-- 6.3 applications, application_events
-- ---------------------------------------------------------------------------
CREATE TABLE applications (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id        uuid         NOT NULL,
  link_id         uuid         NOT NULL,
  position_key    varchar(100) NOT NULL,
  company_name    varchar(200),
  position_title  varchar(300),
  employment_type varchar(100),
  experience      varchar(100),
  location        text,
  status          varchar(20)  NOT NULL DEFAULT 'INTERESTED'
                    CHECK (status IN ('INTERESTED','PLANNED','APPLIED','IN_PROGRESS','ACCEPTED','REJECTED','WITHDRAWN')),
  applied_at      timestamptz,
  notes           text         NOT NULL DEFAULT '',
  field_meta      jsonb        NOT NULL DEFAULT '{}',
  review_status   varchar(20)  NOT NULL DEFAULT 'PENDING'
                    CHECK (review_status IN ('PENDING','CONFIRMED','NOT_REQUIRED')),
  archived_at     timestamptz,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  updated_at      timestamptz  NOT NULL DEFAULT now(),
  version         bigint       NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (id, owner_id),
  UNIQUE (owner_id, link_id, position_key),
  FOREIGN KEY (link_id, owner_id) REFERENCES links(id, owner_id) ON DELETE CASCADE
);
CREATE INDEX idx_applications_owner_status ON applications(owner_id, status, created_at DESC, id);

CREATE TABLE application_events (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id         uuid        NOT NULL,
  application_id   uuid        NOT NULL,
  type             varchar(32) NOT NULL CHECK (type IN (
                     'DOCUMENT_DEADLINE','NCS','CODING_TEST','AI_ASSESSMENT',
                     'INTERVIEW_1','INTERVIEW_2','FINAL_INTERVIEW',
                     'RESULT_ANNOUNCEMENT','ORIENTATION','CUSTOM')),
  custom_label     varchar(200),
  sort_order       int         NOT NULL DEFAULT 0,
  schedule_kind    varchar(16) NOT NULL DEFAULT 'UNKNOWN'
                     CHECK (schedule_kind IN ('EXACT','DATE_ONLY','UNKNOWN','ROLLING','UNTIL_FILLED')),
  scheduled_at     timestamptz,
  start_at         timestamptz,
  end_at           timestamptz,
  scheduled_date   date,
  timezone         varchar(64) NOT NULL DEFAULT 'Asia/Seoul',
  location         text,
  url              text,
  notes            text        NOT NULL DEFAULT '',
  result           varchar(16) NOT NULL DEFAULT 'NOT_STARTED'
                     CHECK (result IN ('NOT_STARTED','WAITING','PASSED','FAILED','SKIPPED')),
  status           varchar(16) NOT NULL DEFAULT 'UNSCHEDULED'
                     CHECK (status IN ('UNSCHEDULED','SCHEDULED','COMPLETED','CANCELLED')),
  confirmed_at     timestamptz,
  confirmed_by     uuid,
  schedule_version bigint      NOT NULL DEFAULT 1 CHECK (schedule_version > 0),
  field_meta       jsonb       NOT NULL DEFAULT '{}',
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  version          bigint      NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (id, owner_id),
  FOREIGN KEY (application_id, owner_id) REFERENCES applications(id, owner_id) ON DELETE CASCADE,
  -- v1.1 section 6.3 CHECK block
  CHECK (type <> 'CUSTOM' OR NULLIF(btrim(custom_label), '') IS NOT NULL),
  CHECK (confirmed_by IS NULL OR confirmed_by = owner_id),
  CHECK ((confirmed_at IS NULL) = (confirmed_by IS NULL)),
  CHECK (scheduled_at IS NULL OR start_at IS NULL OR scheduled_at = start_at),
  CHECK (end_at IS NULL OR (start_at IS NOT NULL AND end_at >= start_at)),
  CHECK (
    (schedule_kind = 'EXACT' AND scheduled_date IS NULL
      AND (scheduled_at IS NOT NULL OR start_at IS NOT NULL))
    OR (schedule_kind = 'DATE_ONLY' AND scheduled_date IS NOT NULL
      AND scheduled_at IS NULL AND start_at IS NULL AND end_at IS NULL)
    OR (schedule_kind IN ('UNKNOWN','ROLLING','UNTIL_FILLED')
      AND scheduled_date IS NULL AND scheduled_at IS NULL
      AND start_at IS NULL AND end_at IS NULL)
  ),
  CHECK (schedule_kind NOT IN ('ROLLING','UNTIL_FILLED') OR type = 'DOCUMENT_DEADLINE'),
  CHECK (status <> 'SCHEDULED' OR schedule_kind IN ('EXACT','DATE_ONLY'))
);
CREATE INDEX idx_events_application   ON application_events(application_id, sort_order, id);
CREATE INDEX idx_events_owner_start   ON application_events(owner_id, start_at)       WHERE start_at IS NOT NULL;
CREATE INDEX idx_events_owner_sched   ON application_events(owner_id, scheduled_at)   WHERE scheduled_at IS NOT NULL;
CREATE INDEX idx_events_owner_date    ON application_events(owner_id, scheduled_date) WHERE scheduled_date IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 6.4 notification rules, notifications, deliveries, devices, preferences
-- ---------------------------------------------------------------------------
CREATE TABLE notification_rules (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id      uuid        NOT NULL,
  event_id      uuid        NOT NULL,
  channel       varchar(16) NOT NULL CHECK (channel IN ('EMAIL','WEB_PUSH','FCM','APNS','IN_APP')),
  anchor        varchar(16) NOT NULL CHECK (anchor IN ('SCHEDULED_AT','START_AT','END_AT','DATE')),
  mode          varchar(24) NOT NULL CHECK (mode IN ('BEFORE_MINUTES','CALENDAR_DAYS')),
  offset_minutes int,
  offset_days    int,
  local_time     time,
  enabled       boolean     NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  version       bigint      NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (id, event_id, owner_id),
  FOREIGN KEY (event_id, owner_id) REFERENCES application_events(id, owner_id) ON DELETE CASCADE,
  CHECK (
    (mode = 'BEFORE_MINUTES' AND anchor IN ('SCHEDULED_AT','START_AT','END_AT')
      AND offset_minutes BETWEEN 0 AND 43200 AND offset_days IS NULL AND local_time IS NULL)
    OR (mode = 'CALENDAR_DAYS' AND anchor = 'DATE'
      AND offset_days BETWEEN 0 AND 30 AND local_time IS NOT NULL AND offset_minutes IS NULL)
  )
);
CREATE UNIQUE INDEX uq_rule_before_minutes ON notification_rules(event_id, channel, anchor, offset_minutes)
  WHERE mode = 'BEFORE_MINUTES';
CREATE UNIQUE INDEX uq_rule_calendar_days ON notification_rules(event_id, channel, offset_days, local_time)
  WHERE mode = 'CALENDAR_DAYS';
CREATE INDEX idx_rules_event ON notification_rules(event_id);

CREATE TABLE notifications (
  id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id               uuid         NOT NULL,
  event_id               uuid         NOT NULL,
  rule_id                uuid         NOT NULL,
  event_schedule_version bigint       NOT NULL,
  rule_version           bigint       NOT NULL,
  channel                varchar(16)  NOT NULL CHECK (channel IN ('EMAIL','WEB_PUSH','FCM','APNS','IN_APP')),
  scheduled_send_at      timestamptz  NOT NULL,
  expires_at             timestamptz  NOT NULL,
  status                 varchar(20)  NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING','DISPATCHING','COMPLETED','PARTIAL_FAILED','FAILED','CANCELLED')),
  payload                jsonb        NOT NULL DEFAULT '{}',
  idempotency_key        varchar(200) NOT NULL UNIQUE,
  next_attempt_at        timestamptz  NOT NULL DEFAULT now(),
  lease_token            uuid,
  lease_until            timestamptz,
  visible_at             timestamptz,
  read_at                timestamptz,
  completed_at           timestamptz,
  created_at             timestamptz  NOT NULL DEFAULT now(),
  updated_at             timestamptz  NOT NULL DEFAULT now(),
  version                bigint       NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (id, owner_id),
  UNIQUE (rule_id, event_schedule_version, rule_version),
  FOREIGN KEY (rule_id, event_id, owner_id)
    REFERENCES notification_rules(id, event_id, owner_id) ON DELETE CASCADE,
  CHECK (expires_at >= scheduled_send_at)
);
CREATE INDEX idx_notifications_due   ON notifications(scheduled_send_at, id) WHERE status = 'PENDING';
CREATE INDEX idx_notifications_lease ON notifications(lease_until)           WHERE status = 'DISPATCHING';
CREATE INDEX idx_notifications_inbox ON notifications(owner_id, visible_at DESC, id)
  WHERE channel = 'IN_APP' AND visible_at IS NOT NULL;

CREATE TABLE notification_deliveries (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id              uuid         NOT NULL,
  notification_id       uuid         NOT NULL,
  target_key            varchar(200) NOT NULL,
  device_id             uuid,
  subscription_id       uuid,
  destination_ciphertext text,
  status                varchar(16)  NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING','SENDING','ACCEPTED','DELIVERED','UNKNOWN','FAILED','CANCELLED','SUPPRESSED')),
  attempts              int          NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  next_attempt_at       timestamptz  NOT NULL DEFAULT now(),
  lease_token           uuid,
  lease_until           timestamptz,
  idempotency_key       varchar(200) NOT NULL UNIQUE,
  provider_message_id   text,
  first_attempt_at      timestamptz,
  accepted_at           timestamptz,
  delivered_at          timestamptz,
  last_error            varchar(100),
  created_at            timestamptz  NOT NULL DEFAULT now(),
  updated_at            timestamptz  NOT NULL DEFAULT now(),
  version               bigint       NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (notification_id, target_key),
  FOREIGN KEY (notification_id, owner_id) REFERENCES notifications(id, owner_id) ON DELETE CASCADE,
  CHECK (device_id IS NULL OR subscription_id IS NULL)
);
CREATE INDEX idx_deliveries_due    ON notification_deliveries(next_attempt_at, id) WHERE status = 'PENDING';
CREATE INDEX idx_deliveries_lease  ON notification_deliveries(lease_until)         WHERE status = 'SENDING';
CREATE INDEX idx_deliveries_pmid   ON notification_deliveries(provider_message_id);

CREATE TABLE user_devices (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id        uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  installation_id uuid        NOT NULL UNIQUE,
  platform        varchar(8)  NOT NULL CHECK (platform IN ('ANDROID','IOS')),
  push_provider   varchar(8)  NOT NULL CHECK (push_provider IN ('FCM','APNS')),
  token_ciphertext text       NOT NULL,
  token_hash      char(64)    NOT NULL,
  token_version   bigint      NOT NULL DEFAULT 1,
  enabled         boolean     NOT NULL DEFAULT true,
  last_seen_at    timestamptz NOT NULL DEFAULT now(),
  revoked_at      timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  version         bigint      NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (id, owner_id),
  UNIQUE (push_provider, token_hash)
);

CREATE TABLE push_subscriptions (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id             uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  endpoint_ciphertext  text        NOT NULL,
  endpoint_hash        char(64)    NOT NULL UNIQUE,
  p256dh_ciphertext    text        NOT NULL,
  auth_ciphertext      text        NOT NULL,
  expiration_at        timestamptz,
  enabled              boolean     NOT NULL DEFAULT true,
  last_seen_at         timestamptz NOT NULL DEFAULT now(),
  revoked_at           timestamptz,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  version              bigint      NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (id, owner_id)
);

CREATE TABLE notification_channel_preferences (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id   uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  channel    varchar(16) NOT NULL CHECK (channel IN ('EMAIL','WEB_PUSH','FCM','APNS','IN_APP')),
  enabled    boolean     NOT NULL DEFAULT false,
  consent_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version    bigint      NOT NULL DEFAULT 1 CHECK (version > 0),
  UNIQUE (owner_id, channel)
);

CREATE TABLE provider_events (
  provider          varchar(32)  NOT NULL,
  provider_event_id varchar(200) NOT NULL,
  provider_message_id text,
  event_type        varchar(32)  NOT NULL,
  occurred_at       timestamptz,
  processed_at      timestamptz,
  created_at        timestamptz  NOT NULL DEFAULT now(),
  updated_at        timestamptz  NOT NULL DEFAULT now(),
  PRIMARY KEY (provider, provider_event_id)
);

CREATE TABLE deletion_jobs (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id       uuid REFERENCES users(id) ON DELETE SET NULL,
  subject_hash   char(64)    NOT NULL,
  status         varchar(16) NOT NULL,
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  storage_prefix text,
  last_error     varchar(100),
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  version        bigint      NOT NULL DEFAULT 1 CHECK (version > 0)
);

-- ---------------------------------------------------------------------------
-- updated_at maintenance trigger (JPA also bumps version; native updates must too)
-- ---------------------------------------------------------------------------
CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

DO $$
DECLARE tab text;
BEGIN
  FOREACH tab IN ARRAY ARRAY[
    'users','auth_identities','password_credentials','auth_action_tokens',
    'links','capture_assets','extraction_runs','public_parse_cache',
    'usage_budgets','usage_ledger','api_idempotency',
    'applications','application_events',
    'notification_rules','notifications','notification_deliveries',
    'user_devices','push_subscriptions','notification_channel_preferences',
    'provider_events','deletion_jobs'
  ]
  LOOP
    EXECUTE format(
      'CREATE TRIGGER trg_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
      tab);
  END LOOP;
END;
$$;

COMMIT;
