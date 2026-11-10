ALTER TABLE telemetry_service_indicator ADD COLUMN maintenance_cycle_id uuid;
ALTER TABLE telemetry_service_indicator ADD COLUMN last_cycle_advance_key varchar(128);
ALTER TABLE telemetry_service_indicator ADD COLUMN last_service_reference varchar(128);

UPDATE telemetry_service_indicator
SET maintenance_cycle_id = (
    substr(md5(vehicle_id || ':' || indicator_code || ':initial-cycle'), 1, 8) || '-' ||
    substr(md5(vehicle_id || ':' || indicator_code || ':initial-cycle'), 9, 4) || '-' ||
    substr(md5(vehicle_id || ':' || indicator_code || ':initial-cycle'), 13, 4) || '-' ||
    substr(md5(vehicle_id || ':' || indicator_code || ':initial-cycle'), 17, 4) || '-' ||
    substr(md5(vehicle_id || ':' || indicator_code || ':initial-cycle'), 21, 12)
)::uuid
WHERE maintenance_cycle_id IS NULL;

ALTER TABLE telemetry_service_indicator ALTER COLUMN maintenance_cycle_id SET NOT NULL;
CREATE UNIQUE INDEX telemetry_service_indicator_cycle_identity_idx
    ON telemetry_service_indicator(vehicle_id, indicator_code, maintenance_cycle_id);
