create table vehicle_reference (
    vehicle_id varchar(64) primary key check (btrim(vehicle_id) <> ''),
    external_vehicle_reference varchar(128), model_code varchar(64), model_year integer,
    market_code varchar(16), powertrain_code varchar(32), source_version bigint,
    source_updated_at timestamptz, refreshed_at timestamptz not null default current_timestamp,
    check (model_year is null or model_year between 1886 and 9999),
    check (source_version is null or source_version >= 0)
);
create index vehicle_reference_plan_selection_idx on vehicle_reference (market_code, model_code, model_year, powertrain_code);

create table vehicle_maintenance_status (
    vehicle_id varchar(64) primary key check (btrim(vehicle_id) <> ''),
    odometer_km bigint not null check (odometer_km >= 0), source_timestamp timestamptz not null,
    source_version bigint check (source_version is null or source_version >= 0),
    payload_checksum char(64) not null check (payload_checksum ~ '^[0-9a-f]{64}$'),
    accepted_at timestamptz not null default current_timestamp, version bigint not null default 0
);

create table maintenance_indicator (
    id uuid primary key, vehicle_id varchar(64) not null references vehicle_maintenance_status(vehicle_id) on delete restrict,
    indicator_code varchar(64) not null check (btrim(indicator_code) <> ''), due_odometer_km bigint,
    due_date date, remaining_distance_km bigint, remaining_days integer,
    severity varchar(16) not null check (severity in ('INFO','WARNING','CRITICAL')),
    source_timestamp timestamptz not null,
    unique (vehicle_id, indicator_code),
    check (due_odometer_km is null or due_odometer_km >= 0),
    check (remaining_distance_km is null or remaining_distance_km >= 0),
    check (remaining_days is null or remaining_days >= 0)
);
create index maintenance_indicator_vehicle_source_idx on maintenance_indicator (vehicle_id, source_timestamp desc);

create table maintenance_plan (
    id uuid primary key, maintenance_type varchar(64) not null check (btrim(maintenance_type) <> ''),
    applicability_scope_identity varchar(128) not null check (btrim(applicability_scope_identity) <> ''),
    model_code varchar(64), model_year_from integer, model_year_to integer, market_code varchar(16), powertrain_code varchar(32),
    distance_threshold_km bigint, time_threshold_days integer,
    severity_policy varchar(16) not null check (severity_policy in ('INFO','WARNING','CRITICAL')),
    policy_version varchar(64) not null check (btrim(policy_version) <> ''), valid_from date not null, valid_to date,
    created_at timestamptz not null default current_timestamp,
    unique (maintenance_type, applicability_scope_identity, policy_version, valid_from),
    check (model_year_from is null or model_year_from between 1886 and 9999),
    check (model_year_to is null or model_year_to between 1886 and 9999),
    check (model_year_to is null or model_year_from is null or model_year_to >= model_year_from),
    check (distance_threshold_km is null or distance_threshold_km > 0),
    check (time_threshold_days is null or time_threshold_days > 0),
    check (distance_threshold_km is not null or time_threshold_days is not null),
    check (valid_to is null or valid_to >= valid_from)
);
create index maintenance_plan_lookup_idx on maintenance_plan (maintenance_type, market_code, model_code, powertrain_code, valid_from, valid_to);
create index maintenance_plan_scope_version_idx on maintenance_plan (applicability_scope_identity, policy_version);

create table reminder (
    id uuid primary key, vehicle_id varchar(64) not null check (btrim(vehicle_id) <> ''),
    maintenance_type varchar(64) not null check (btrim(maintenance_type) <> ''),
    maintenance_cycle_reference char(64) not null check (maintenance_cycle_reference ~ '^[0-9a-f]{64}$'),
    due_odometer_km bigint, due_date date, remaining_distance_km bigint, remaining_days integer,
    severity varchar(16) not null check (severity in ('INFO','WARNING','CRITICAL')),
    policy_version varchar(64) not null check (btrim(policy_version) <> ''),
    status varchar(16) not null check (status in ('PENDING','SCHEDULED','ACKNOWLEDGED','DISMISSED','EXPIRED')),
    next_action_at timestamptz, deduplication_key varchar(256) not null check (btrim(deduplication_key) <> ''),
    created_at timestamptz not null default current_timestamp, updated_at timestamptz not null default current_timestamp,
    version bigint not null default 0,
    unique (vehicle_id, maintenance_type, maintenance_cycle_reference), unique (deduplication_key),
    check (due_odometer_km is not null or due_date is not null),
    check (due_odometer_km is null or due_odometer_km >= 0),
    check (remaining_distance_km is null or remaining_distance_km >= 0), check (remaining_days is null or remaining_days >= 0)
);
create index reminder_vehicle_status_idx on reminder (vehicle_id, status, updated_at desc);
create index reminder_scheduler_idx on reminder (status, next_action_at) where next_action_at is not null;
create index reminder_due_date_idx on reminder (due_date) where due_date is not null;

create table reminder_notification (
    id uuid primary key, reminder_id uuid not null references reminder(id) on delete restrict,
    recipient_reference varchar(256), channel varchar(32) not null check (btrim(channel) <> ''),
    status varchar(16) not null default 'PENDING' check (status in ('PENDING','PUBLISHED','ACCEPTED','DELIVERED','FAILED')),
    idempotency_key varchar(256) not null check (btrim(idempotency_key) <> ''), notification_provider_request_id varchar(128),
    attempt_count integer not null default 0 check (attempt_count >= 0),
    created_at timestamptz not null default current_timestamp, updated_at timestamptz not null default current_timestamp,
    published_at timestamptz, accepted_at timestamptz, delivered_at timestamptz, failed_at timestamptz, last_failure_code varchar(64),
    unique (idempotency_key), unique (notification_provider_request_id),
    check (status not in ('PUBLISHED','ACCEPTED','DELIVERED') or published_at is not null),
    check (status not in ('ACCEPTED','DELIVERED') or accepted_at is not null),
    check (status <> 'DELIVERED' or delivered_at is not null), check (status <> 'FAILED' or failed_at is not null)
);
create index reminder_notification_history_idx on reminder_notification (reminder_id, created_at desc);
create index reminder_notification_status_idx on reminder_notification (status, updated_at);

create table processed_event (
    source_system varchar(64) not null check (btrim(source_system) <> ''), event_id uuid not null,
    consumer_name varchar(128) not null check (btrim(consumer_name) <> ''),
    payload_checksum char(64) not null check (payload_checksum ~ '^[0-9a-f]{64}$'),
    processed_at timestamptz not null default current_timestamp,
    primary key (source_system, event_id, consumer_name)
);
create index processed_event_cleanup_idx on processed_event (processed_at);

create table idempotency_record (
    caller_id varchar(128) not null check (btrim(caller_id) <> ''), idempotency_key varchar(256) not null check (btrim(idempotency_key) <> ''),
    request_hash char(64) not null check (request_hash ~ '^[0-9a-f]{64}$'), response_status smallint not null check (response_status between 100 and 599),
    result_resource_type varchar(64), result_resource_id uuid, created_at timestamptz not null default current_timestamp, expires_at timestamptz not null,
    primary key (caller_id, idempotency_key),
    check ((result_resource_type is null and result_resource_id is null) or (result_resource_type is not null and result_resource_id is not null)),
    check (expires_at > created_at)
);
create index idempotency_record_expiry_idx on idempotency_record (expires_at);

create table outbox_event (
    id uuid primary key, aggregate_type varchar(64) not null check (btrim(aggregate_type) <> ''), aggregate_id uuid not null,
    event_type varchar(128) not null check (btrim(event_type) <> ''), schema_version integer not null check (schema_version >= 1),
    payload jsonb not null check (jsonb_typeof(payload) = 'object'), correlation_id uuid,
    publication_status varchar(16) not null default 'PENDING' check (publication_status in ('PENDING','IN_PROGRESS','PUBLISHED','FAILED')),
    attempt_count integer not null default 0 check (attempt_count >= 0), next_attempt_at timestamptz not null default current_timestamp,
    claimed_at timestamptz, claim_token uuid, claim_expires_at timestamptz, published_at timestamptz, last_failure_code varchar(64),
    created_at timestamptz not null default current_timestamp,
    check (publication_status <> 'PUBLISHED' or published_at is not null),
    check (publication_status <> 'IN_PROGRESS' or (claimed_at is not null and claim_token is not null and claim_expires_at is not null)),
    check (claim_expires_at is null or claim_expires_at > claimed_at)
);
create index outbox_event_publish_idx on outbox_event (publication_status, next_attempt_at, created_at) where publication_status in ('PENDING','FAILED');
create index outbox_event_expired_claim_idx on outbox_event (claim_expires_at) where publication_status = 'IN_PROGRESS';
create index outbox_event_aggregate_idx on outbox_event (aggregate_type, aggregate_id, created_at desc);
