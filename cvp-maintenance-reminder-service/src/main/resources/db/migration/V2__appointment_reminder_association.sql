CREATE TABLE appointment_reminder (
  appointment_id uuid PRIMARY KEY,
  reminder_id uuid NOT NULL REFERENCES reminder(id),
  vehicle_id varchar(64) NOT NULL,
  service_type varchar(64) NOT NULL,
  external_dms_reference varchar(255) NOT NULL,
  booked_at timestamptz NOT NULL DEFAULT current_timestamp,
  completed_at timestamptz
);
CREATE INDEX appointment_reminder_reminder_id_idx ON appointment_reminder(reminder_id);

ALTER TABLE reminder DROP CONSTRAINT reminder_status_check;
ALTER TABLE reminder ADD CONSTRAINT reminder_status_check CHECK (status IN ('PENDING','SCHEDULED','ACKNOWLEDGED','DISMISSED','EXPIRED','COMPLETED'));
