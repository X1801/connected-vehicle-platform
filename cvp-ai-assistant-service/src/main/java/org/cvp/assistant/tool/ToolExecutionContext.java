package org.cvp.assistant.tool;

import java.time.Instant;

public record ToolExecutionContext(String principal, String vehicleId, String correlationId, Instant deadline) {
    public void requireVehicle(String requestedVehicleId) {
        if (!vehicleId.equals(requestedVehicleId)) {
            throw new IllegalArgumentException("Tool vehicle must match the authorized vehicle");
        }
        if (Instant.now().isAfter(deadline)) {
            throw new ToolDeadlineExceededException();
        }
    }
}
