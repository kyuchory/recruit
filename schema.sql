-- 채용 지원 일정 관리 MVP / PostgreSQL 16+ / empty application schema
-- Flyway V1 migration candidate. UUIDs may also be supplied by the application.
BEGIN;
CREATE TABLE users (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 display_name varchar(80) NOT NULL,
 timezone varchar(64) NOT NULL DEFAULT 'Asia/Seoul',
 notifications_enabled boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE oauth_identities (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 provider varchar(16) NOT NULL CHECK (provider IN ('GOOGLE','KAKAO')),
 subject varchar(255) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(provider,subject)
);
CREATE INDEX idx_identity_user ON oauth_identities(user_id);
CREATE TABLE applications (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 company_name varchar(200),
 position varchar(300),
 source_url text,
 canonical_url text,
 url_hash varchar(64),
 status varchar(24) NOT NULL DEFAULT 'SAVED'
  CHECK (status IN ('SAVED','APPLIED','IN_PROGRESS','OFFERED','REJECTED','WITHDRAWN')),
 review_status varchar(24) NOT NULL DEFAULT 'DRAFT'
  CHECK (review_status IN ('DRAFT','NEEDS_REVIEW','CONFIRMED')),
 notes text NOT NULL DEFAULT '',
 applied_at timestamptz,
 archived_at timestamptz,
 version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(id,user_id),
 CHECK ((canonical_url IS NULL AND url_hash IS NULL) OR
        (canonical_url IS NOT NULL AND url_hash IS NOT NULL)),
 CHECK (review_status <> 'CONFIRMED' OR
        (company_name IS NOT NULL AND position IS NOT NULL))
);
-- Archived records also participate: return existing record instead of silently duplicating.
CREATE UNIQUE INDEX uq_application_url ON applications(user_id,url_hash) WHERE url_hash IS NOT NULL;
CREATE INDEX idx_application_list ON applications(user_id,created_at DESC,id DESC);
CREATE INDEX idx_application_status ON applications(user_id,status) WHERE archived_at IS NULL;
CREATE TABLE parse_jobs (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 application_id uuid NOT NULL,
 user_id uuid NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'PENDING'
  CHECK (status IN ('PENDING','RUNNING','RETRY_WAIT','NEEDS_REVIEW','CONFIRMED','FAILED','CANCELED')),
 stage varchar(24) NOT NULL DEFAULT 'FETCH'
  CHECK (stage IN ('FETCH','METADATA','DOM','VISION','VALIDATE','COMPLETE')),
 input_kind varchar(12) NOT NULL DEFAULT 'URL' CHECK(input_kind IN ('URL','IMAGE','TEXT')),
 source_url text,
 input_text text,
 result_json jsonb,
 parser_version varchar(64) NOT NULL,
 model_name varchar(100),
 prompt_version varchar(64),
 input_tokens integer NOT NULL DEFAULT 0 CHECK(input_tokens >= 0),
 output_tokens integer NOT NULL DEFAULT 0 CHECK(output_tokens >= 0),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts >= 0),
 max_attempts integer NOT NULL DEFAULT 3 CHECK(max_attempts BETWEEN 1 AND 10),
 next_run_at timestamptz NOT NULL DEFAULT now(),
 lease_token uuid,
 lease_until timestamptz,
 error_code varchar(64),
 error_detail varchar(1000),
 started_at timestamptz,
 finished_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(application_id,user_id) REFERENCES applications(id,user_id) ON DELETE CASCADE,
 UNIQUE(id,application_id,user_id),
 CHECK ((lease_token IS NULL) = (lease_until IS NULL)),
 CHECK (input_kind <> 'URL' OR source_url IS NOT NULL),
 CHECK (input_kind <> 'TEXT' OR input_text IS NOT NULL)
);
CREATE UNIQUE INDEX uq_parse_active ON parse_jobs(application_id)
 WHERE status IN ('PENDING','RUNNING','RETRY_WAIT','NEEDS_REVIEW');
CREATE INDEX idx_parse_due ON parse_jobs(next_run_at,id) WHERE status IN ('PENDING','RETRY_WAIT');
CREATE INDEX idx_parse_lease ON parse_jobs(lease_until) WHERE status='RUNNING';
CREATE INDEX idx_parse_history ON parse_jobs(application_id,created_at DESC);
CREATE INDEX idx_parse_user ON parse_jobs(user_id);
CREATE TABLE artifacts (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 parse_job_id uuid,
 application_id uuid,
 object_key text NOT NULL UNIQUE,
 media_type varchar(100) NOT NULL,
 byte_size bigint NOT NULL CHECK(byte_size BETWEEN 1 AND 10485760),
 sha256 varchar(64),
 status varchar(16) NOT NULL DEFAULT 'UPLOADING'
  CHECK(status IN ('UPLOADING','READY','REJECTED','EXPIRED')),
 expires_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(parse_job_id,application_id,user_id)
  REFERENCES parse_jobs(id,application_id,user_id) ON DELETE CASCADE,
 CHECK ((parse_job_id IS NULL) = (application_id IS NULL))
);
CREATE INDEX idx_artifact_user ON artifacts(user_id);
CREATE INDEX idx_artifact_job ON artifacts(parse_job_id);
CREATE INDEX idx_artifact_expiry ON artifacts(expires_at);
CREATE TABLE application_events (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 application_id uuid NOT NULL,
 user_id uuid NOT NULL,
 type varchar(32) NOT NULL CHECK(type IN
  ('DOCUMENT_DEADLINE','CODING_TEST','NCS','AI_ASSESSMENT','INTERVIEW','RESULT_ANNOUNCEMENT','CUSTOM')),
 title varchar(200) NOT NULL,
 round integer CHECK(round >= 1),
 sort_order integer NOT NULL DEFAULT 0,
 status varchar(24) NOT NULL DEFAULT 'PLANNED'
  CHECK(status IN ('PLANNED','COMPLETED','CANCELED')),
 result varchar(16) NOT NULL DEFAULT 'UNKNOWN'
  CHECK(result IN ('UNKNOWN','WAITING','PASSED','FAILED','NOT_APPLICABLE')),
 date_precision varchar(12) NOT NULL DEFAULT 'UNKNOWN'
  CHECK(date_precision IN ('UNKNOWN','DATE_ONLY','DATETIME')),
 scheduled_date date,
 scheduled_at timestamptz,
 end_at timestamptz,
 timezone varchar(64) NOT NULL DEFAULT 'Asia/Seoul',
 confirmed_at timestamptz,
 source varchar(12) NOT NULL DEFAULT 'MANUAL' CHECK(source IN ('MANUAL','PARSED')),
 source_parse_job_id uuid,
 source_candidate_id varchar(64),
 location varchar(500),
 notes text NOT NULL DEFAULT '',
 schedule_version bigint NOT NULL DEFAULT 1 CHECK(schedule_version >= 1),
 version bigint NOT NULL DEFAULT 0 CHECK(version >= 0),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(id,user_id),
 FOREIGN KEY(application_id,user_id) REFERENCES applications(id,user_id) ON DELETE CASCADE,
 FOREIGN KEY(source_parse_job_id,application_id,user_id)
  REFERENCES parse_jobs(id,application_id,user_id),
 CHECK (
  (date_precision='UNKNOWN' AND scheduled_date IS NULL AND scheduled_at IS NULL AND end_at IS NULL) OR
  (date_precision='DATE_ONLY' AND scheduled_date IS NOT NULL AND scheduled_at IS NULL AND end_at IS NULL) OR
  (date_precision='DATETIME' AND scheduled_date IS NULL AND scheduled_at IS NOT NULL)
 ),
 CHECK(end_at IS NULL OR end_at >= scheduled_at),
 CHECK((source_parse_job_id IS NULL) = (source_candidate_id IS NULL))
);
CREATE UNIQUE INDEX uq_event_candidate ON application_events(source_parse_job_id,source_candidate_id)
 WHERE source_parse_job_id IS NOT NULL;
CREATE INDEX idx_event_application ON application_events(application_id,sort_order,id);
CREATE INDEX idx_event_calendar ON application_events(user_id,scheduled_at) WHERE status='PLANNED';
CREATE INDEX idx_event_date ON application_events(user_id,scheduled_date) WHERE date_precision='DATE_ONLY';
CREATE TABLE notification_rules (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 event_id uuid NOT NULL,
 user_id uuid NOT NULL,
 remind_before_minutes integer NOT NULL CHECK(remind_before_minutes BETWEEN 0 AND 43200),
 channel_policy varchar(16) NOT NULL DEFAULT 'AUTO' CHECK(channel_policy IN ('AUTO','EMAIL','PUSH')),
 enabled boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(event_id,user_id) REFERENCES application_events(id,user_id) ON DELETE CASCADE,
 UNIQUE(event_id,remind_before_minutes),
 UNIQUE(id,event_id,user_id)
);
CREATE INDEX idx_rule_user ON notification_rules(user_id);
CREATE TABLE notification_endpoints (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 channel varchar(16) NOT NULL CHECK(channel IN ('WEB_PUSH','EMAIL','FCM','APNS')),
 platform varchar(12) NOT NULL CHECK(platform IN ('WEB','ANDROID','IOS','NONE')),
 environment varchar(12) NOT NULL DEFAULT 'PRODUCTION' CHECK(environment IN ('PRODUCTION','SANDBOX')),
 address_ciphertext text NOT NULL,
 address_hash varchar(64) NOT NULL,
 credentials_ciphertext text,
 device_name varchar(100),
 verified_at timestamptz,
 enabled boolean NOT NULL DEFAULT true,
 last_seen_at timestamptz NOT NULL DEFAULT now(),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(id,user_id),
 UNIQUE(channel,environment,address_hash),
 CHECK ((channel='EMAIL' AND platform='NONE') OR
        (channel='WEB_PUSH' AND platform='WEB' AND credentials_ciphertext IS NOT NULL) OR
        (channel='FCM' AND platform='ANDROID') OR (channel='APNS' AND platform='IOS'))
);
CREATE INDEX idx_endpoint_user ON notification_endpoints(user_id,channel) WHERE enabled;
CREATE UNIQUE INDEX uq_email_endpoint ON notification_endpoints(user_id) WHERE channel='EMAIL' AND enabled;
CREATE TABLE notifications (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL,
 event_id uuid NOT NULL,
 rule_id uuid NOT NULL,
 schedule_version bigint NOT NULL,
 scheduled_send_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','READY','CANCELED','SKIPPED')),
 skip_reason varchar(64),
 fallback_created boolean NOT NULL DEFAULT false,
 read_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(rule_id,event_id,user_id) REFERENCES notification_rules(id,event_id,user_id) ON DELETE CASCADE,
 UNIQUE(id,user_id),
 UNIQUE(rule_id,schedule_version),
 CHECK(expires_at >= scheduled_send_at)
);
CREATE INDEX idx_notification_due ON notifications(scheduled_send_at,id) WHERE status='PENDING';
CREATE INDEX idx_notification_event ON notifications(event_id);
CREATE INDEX idx_notification_inbox ON notifications(user_id,scheduled_send_at DESC,id DESC);
CREATE TABLE notification_deliveries (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 notification_id uuid NOT NULL,
 user_id uuid NOT NULL,
 endpoint_id uuid NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'PENDING' CHECK(status IN
  ('PENDING','SENDING','RETRY_WAIT','ACCEPTED','UNKNOWN','FAILED','CANCELED')),
 payload_json jsonb NOT NULL,
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts >= 0),
 next_run_at timestamptz NOT NULL DEFAULT now(),
 lease_token uuid,
 lease_until timestamptz,
 provider_message_id varchar(255),
 last_error_code varchar(64),
 accepted_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(notification_id,user_id) REFERENCES notifications(id,user_id) ON DELETE CASCADE,
 FOREIGN KEY(endpoint_id,user_id) REFERENCES notification_endpoints(id,user_id),
 UNIQUE(notification_id,endpoint_id),
 CHECK((lease_token IS NULL) = (lease_until IS NULL))
);
CREATE INDEX idx_delivery_due ON notification_deliveries(next_run_at,id) WHERE status IN ('PENDING','RETRY_WAIT');
CREATE INDEX idx_delivery_lease ON notification_deliveries(lease_until) WHERE status='SENDING';
CREATE INDEX idx_delivery_endpoint ON notification_deliveries(endpoint_id);
CREATE INDEX idx_delivery_user ON notification_deliveries(user_id);
CREATE TABLE delivery_attempts (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 delivery_id uuid NOT NULL REFERENCES notification_deliveries(id) ON DELETE CASCADE,
 attempt_no integer NOT NULL CHECK(attempt_no > 0),
 lease_token uuid NOT NULL,
 outcome varchar(16) NOT NULL CHECK(outcome IN ('STARTED','ACCEPTED','RETRYABLE','PERMANENT','UNKNOWN')),
 provider_status integer,
 error_code varchar(64),
 started_at timestamptz NOT NULL DEFAULT now(),
 finished_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(delivery_id,attempt_no)
);
CREATE TABLE idempotency_keys (
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 scope varchar(160) NOT NULL,
 key varchar(128) NOT NULL,
 request_hash varchar(64) NOT NULL,
 response_status integer NOT NULL,
 response_body jsonb,
 response_headers jsonb NOT NULL DEFAULT '{}',
 expires_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(user_id,scope,key)
);
CREATE INDEX idx_idempotency_expiry ON idempotency_keys(expires_at);
CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW.updated_at = now();
 RETURN NEW;
END;
$$;
DO $$
DECLARE tab text;
BEGIN
 FOREACH tab IN ARRAY ARRAY['users','oauth_identities','applications','parse_jobs','artifacts',
 'application_events','notification_rules','notification_endpoints','notifications',
 'notification_deliveries','delivery_attempts','idempotency_keys']
 LOOP
  EXECUTE format('CREATE TRIGGER trg_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',tab);
 END LOOP;
END;
$$;
COMMIT;
