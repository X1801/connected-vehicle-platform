CREATE TABLE telemetry_vehicle_state (
    vehicle_id VARCHAR(100) PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_version BIGINT NOT NULL CHECK (source_version > 0),
    source_timestamp TIMESTAMPTZ NOT NULL,
    odometer_km BIGINT NOT NULL CHECK (odometer_km >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE telemetry_service_indicator (
    vehicle_id VARCHAR(100) NOT NULL REFERENCES telemetry_vehicle_state(vehicle_id) ON DELETE CASCADE,
    indicator_code VARCHAR(64) NOT NULL,
    due_odometer_km BIGINT,
    due_date DATE,
    remaining_distance_km BIGINT,
    remaining_days INTEGER,
    severity VARCHAR(32) NOT NULL,
    source_timestamp TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (vehicle_id, indicator_code),
    CHECK (due_odometer_km IS NULL OR due_odometer_km >= 0),
    CHECK (remaining_distance_km IS NULL OR remaining_distance_km >= 0),
    CHECK (remaining_days IS NULL OR remaining_days >= 0)
);

CREATE TABLE telemetry_published_event (
    event_id UUID PRIMARY KEY,
    vehicle_id VARCHAR(100) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_version BIGINT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_telemetry_published_event_vehicle ON telemetry_published_event(vehicle_id, published_at);
