BEGIN;

ALTER TABLE career_profile_items
  ADD COLUMN field_values text NOT NULL DEFAULT ''
    CHECK (char_length(field_values) <= 140000);

COMMIT;
