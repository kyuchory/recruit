-- ============================================================================
-- V2 -- align the `version` CHECK with JPA @Version semantics.
--
-- V1 (from the design doc convention) declared `version bigint DEFAULT 1
-- CHECK (version > 0)`. That assumes a service-managed version. With JPA
-- `@Version` (Step 5 requirement) Hibernate seeds a numeric version at 0 on the
-- first INSERT, which violates `> 0`. We relax the lower bound to `>= 0` on the
-- tables that now have JPA entities. Optimistic locking and the NOT NULL
-- default are unchanged; `schedule_version` (service-managed) keeps `> 0`.
--
-- Apply the same change to the remaining tables as their entities are added.
-- ============================================================================
BEGIN;

ALTER TABLE users              DROP CONSTRAINT users_version_check;
ALTER TABLE users              ADD  CONSTRAINT users_version_check CHECK (version >= 0);

ALTER TABLE links              DROP CONSTRAINT links_version_check;
ALTER TABLE links              ADD  CONSTRAINT links_version_check CHECK (version >= 0);

ALTER TABLE applications       DROP CONSTRAINT applications_version_check;
ALTER TABLE applications       ADD  CONSTRAINT applications_version_check CHECK (version >= 0);

ALTER TABLE application_events DROP CONSTRAINT application_events_version_check;
ALTER TABLE application_events ADD  CONSTRAINT application_events_version_check CHECK (version >= 0);

COMMIT;
