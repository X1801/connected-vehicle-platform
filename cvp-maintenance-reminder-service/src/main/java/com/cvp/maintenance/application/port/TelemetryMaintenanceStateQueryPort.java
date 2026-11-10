package com.cvp.maintenance.application.port;

import com.cvp.maintenance.adapter.inbound.kafka.TelemetryMaintenanceStateEvent;

import java.util.Optional;

/**
 * Boundary for on-demand/reconciliation reads from Telemetry.
 */
public interface TelemetryMaintenanceStateQueryPort {
    Optional<TelemetryMaintenanceStateEvent> findMaintenanceState(String vehicleId);
}
