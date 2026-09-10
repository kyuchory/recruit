-- extraction_runs now has a JPA @Version entity (Step 8); same relaxation as V2.
BEGIN;
ALTER TABLE extraction_runs DROP CONSTRAINT extraction_runs_version_check;
ALTER TABLE extraction_runs ADD  CONSTRAINT extraction_runs_version_check CHECK (version >= 0);
COMMIT;
