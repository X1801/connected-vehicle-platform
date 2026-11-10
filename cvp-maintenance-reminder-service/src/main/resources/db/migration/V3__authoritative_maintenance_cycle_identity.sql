ALTER TABLE maintenance_indicator ADD COLUMN maintenance_cycle_id uuid;
CREATE INDEX maintenance_indicator_cycle_identity_idx
    ON maintenance_indicator(vehicle_id, indicator_code, maintenance_cycle_id);

ALTER TABLE reminder ADD COLUMN authoritative_cycle_id uuid;
CREATE UNIQUE INDEX reminder_authoritative_cycle_identity_uq
    ON reminder(vehicle_id, maintenance_type, authoritative_cycle_id)
    WHERE authoritative_cycle_id IS NOT NULL;
