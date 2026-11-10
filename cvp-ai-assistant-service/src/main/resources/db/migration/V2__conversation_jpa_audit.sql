ALTER TABLE conversation ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX IF NOT EXISTS conversation_principal_vehicle_idx ON conversation(principal, vehicle_id);
