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
