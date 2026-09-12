BEGIN;

CREATE TABLE application_essay_questions (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id       uuid         NOT NULL,
  application_id uuid         NOT NULL,
  sort_order     int          NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
  question_text  text         NOT NULL CHECK (char_length(question_text) BETWEEN 1 AND 5000),
  limit_type     varchar(32)  NOT NULL DEFAULT 'NONE' CHECK (limit_type IN (
                   'NONE','CHARACTERS_WITH_SPACES','CHARACTERS_WITHOUT_SPACES',
                   'UTF8_BYTES','KOREAN_2_BYTES')),
  limit_value    int,
  answer_text    text         NOT NULL DEFAULT '' CHECK (char_length(answer_text) <= 100000),
  status         varchar(16)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','COMPLETED')),
  created_at     timestamptz  NOT NULL DEFAULT now(),
  updated_at     timestamptz  NOT NULL DEFAULT now(),
  version        bigint       NOT NULL DEFAULT 0 CHECK (version >= 0),
  UNIQUE (id, owner_id),
  FOREIGN KEY (application_id, owner_id) REFERENCES applications(id, owner_id) ON DELETE CASCADE,
  CHECK ((limit_type = 'NONE' AND limit_value IS NULL)
      OR (limit_type <> 'NONE' AND limit_value BETWEEN 1 AND 1000000))
);
CREATE INDEX idx_essay_questions_application
  ON application_essay_questions(application_id, sort_order, id);

CREATE TABLE application_essay_revisions (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id            uuid         NOT NULL,
  application_id      uuid         NOT NULL,
  question_id         uuid         NOT NULL,
  revision_no         int          NOT NULL CHECK (revision_no > 0),
  label               varchar(100),
  question_text       text         NOT NULL,
  limit_type          varchar(32)  NOT NULL,
  limit_value         int,
  answer_text         text         NOT NULL,
  character_count     int          NOT NULL CHECK (character_count >= 0),
  no_space_count      int          NOT NULL CHECK (no_space_count >= 0),
  utf8_byte_count     int          NOT NULL CHECK (utf8_byte_count >= 0),
  korean_2byte_count  int          NOT NULL CHECK (korean_2byte_count >= 0),
  created_at          timestamptz  NOT NULL DEFAULT now(),
  updated_at          timestamptz  NOT NULL DEFAULT now(),
  version             bigint       NOT NULL DEFAULT 0 CHECK (version >= 0),
  UNIQUE (question_id, revision_no),
  FOREIGN KEY (application_id, owner_id) REFERENCES applications(id, owner_id) ON DELETE CASCADE,
  FOREIGN KEY (question_id, owner_id) REFERENCES application_essay_questions(id, owner_id) ON DELETE CASCADE
);
CREATE INDEX idx_essay_revisions_question
  ON application_essay_revisions(question_id, revision_no DESC);

COMMIT;
