-- Review and apply as PostgreSQL administrator. Contains no credentials.
BEGIN;
CREATE SCHEMA IF NOT EXISTS notifications;
CREATE TABLE IF NOT EXISTS notifications.delivery_history (
  event_id UUID PRIMARY KEY,
  user_id VARCHAR(256),
  event_name VARCHAR(80) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','SENT','FAILED')),
  failure_code VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS delivery_history_owner ON notifications.delivery_history(user_id,created_at DESC);

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'notifications_app') THEN
    CREATE ROLE notifications_app NOLOGIN;
  END IF;
END $$;
REVOKE ALL ON SCHEMA notifications FROM PUBLIC;
GRANT USAGE ON SCHEMA notifications TO notifications_app;
GRANT SELECT, INSERT, UPDATE ON notifications.delivery_history TO notifications_app;
ALTER TABLE notifications.delivery_history ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='notifications'
      AND tablename='delivery_history' AND policyname='notifications_service_access') THEN
    CREATE POLICY notifications_service_access ON notifications.delivery_history
      FOR ALL TO notifications_app USING (true) WITH CHECK (true);
  END IF;
END $$;
COMMIT;
-- Assign LOGIN and a strong password separately through the administrator UI.
