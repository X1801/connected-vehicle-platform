create table inventory_vehicle (
  vehicle_id varchar(64) primary key,
  model_code varchar(64) not null,
  model_year integer not null,
  market_code varchar(16) not null,
  powertrain_code varchar(32) not null,
  supported_capabilities jsonb not null,
  source_version bigint not null,
  updated_at timestamptz not null default current_timestamp
);
