CREATE TABLE inventory_change_outbox (
  event_id uuid PRIMARY KEY, vehicle_id varchar(64) NOT NULL REFERENCES inventory_vehicle(vehicle_id),
  event_type varchar(32) NOT NULL, source_version bigint NOT NULL, occurred_at timestamptz NOT NULL,
  publication_status varchar(16) NOT NULL DEFAULT 'PENDING', attempt_count integer NOT NULL DEFAULT 0,
  published_at timestamptz, created_at timestamptz NOT NULL DEFAULT current_timestamp
);
CREATE INDEX inventory_change_outbox_pending_idx ON inventory_change_outbox(publication_status, created_at);
