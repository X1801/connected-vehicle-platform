package org.cvp.assistant.client.telemetry;

import tools.jackson.databind.JsonNode;

public interface TelemetryMaintenanceStateClientPort {
    JsonNode maintenanceState(String vehicleId);
}
