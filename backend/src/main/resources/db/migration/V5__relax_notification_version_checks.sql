-- notification_rules + notifications now have JPA @Version entities (Step 14/15).
BEGIN;
ALTER TABLE notification_rules DROP CONSTRAINT notification_rules_version_check;
ALTER TABLE notification_rules ADD  CONSTRAINT notification_rules_version_check CHECK (version >= 0);
ALTER TABLE notifications DROP CONSTRAINT notifications_version_check;
ALTER TABLE notifications ADD  CONSTRAINT notifications_version_check CHECK (version >= 0);
COMMIT;
