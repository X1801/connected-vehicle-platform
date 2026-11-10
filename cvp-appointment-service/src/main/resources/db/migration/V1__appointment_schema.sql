CREATE TABLE appointment (
  appointment_id UUID PRIMARY KEY, vehicle_id VARCHAR(128) NOT NULL, dealer_id VARCHAR(128) NOT NULL,
  dealer_name VARCHAR(255) NOT NULL, slot_id VARCHAR(128) NOT NULL, service_type VARCHAR(128) NOT NULL,
  partner_request_reference VARCHAR(255), external_dms_reference VARCHAR(255), status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, booked_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0, CONSTRAINT appointment_status_check CHECK (status IN ('PENDING','BOOKED','COMPLETED'))
);
CREATE INDEX idx_appointment_vehicle_id ON appointment(vehicle_id);
CREATE INDEX idx_appointment_partner_request_reference ON appointment(partner_request_reference);
CREATE INDEX idx_appointment_external_dms_reference ON appointment(external_dms_reference);
CREATE INDEX idx_appointment_status ON appointment(status);
