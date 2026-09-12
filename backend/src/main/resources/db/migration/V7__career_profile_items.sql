BEGIN;

CREATE TABLE career_profile_items (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id    uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category    varchar(24)  NOT NULL CHECK (category IN (
                'PERSONAL','MILITARY','EDUCATION','LANGUAGE','CERTIFICATION',
                'CAREER','AWARD','ACTIVITY','SKILL','PROJECT','STORY')),
  label       varchar(200) NOT NULL CHECK (char_length(btrim(label)) BETWEEN 1 AND 200),
  value_text  text         NOT NULL DEFAULT '' CHECK (char_length(value_text) <= 30000),
  details     text         NOT NULL DEFAULT '' CHECK (char_length(details) <= 140000),
  started_on  date,
  ended_on    date,
  sensitive   boolean      NOT NULL DEFAULT false,
  sort_order  int          NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
  created_at  timestamptz  NOT NULL DEFAULT now(),
  updated_at  timestamptz  NOT NULL DEFAULT now(),
  version     bigint       NOT NULL DEFAULT 0 CHECK (version >= 0),
  UNIQUE (id, owner_id),
  CHECK (ended_on IS NULL OR started_on IS NULL OR ended_on >= started_on)
);
CREATE INDEX idx_career_profile_owner_category
  ON career_profile_items(owner_id, category, sort_order, id);

COMMIT;
