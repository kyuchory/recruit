-- capture_assets now has a JPA @Version entity (Step 12); same relaxation as V2.
BEGIN;
ALTER TABLE capture_assets DROP CONSTRAINT capture_assets_version_check;
ALTER TABLE capture_assets ADD  CONSTRAINT capture_assets_version_check CHECK (version >= 0);
COMMIT;
