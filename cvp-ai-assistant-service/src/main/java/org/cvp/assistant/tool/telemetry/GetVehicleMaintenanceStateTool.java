package org.cvp.assistant.tool.telemetry;

import tools.jackson.databind.JsonNode;
import org.cvp.assistant.client.telemetry.TelemetryMaintenanceStateClientPort;
import org.cvp.assistant.client.maintenance.UpstreamException;
import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.tool.AssistantTool;
import org.cvp.assistant.tool.ToolExecutionContext;
import org.cvp.assistant.tool.ToolFactMapper;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "assistant.tools", name = "transport", havingValue = "direct", matchIfMissing = true)
public class GetVehicleMaintenanceStateTool implements AssistantTool<String> {
    private final TelemetryMaintenanceStateClientPort telemetry;

    public GetVehicleMaintenanceStateTool(TelemetryMaintenanceStateClientPort telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public String name() {
        return "getVehicleMaintenanceState";
    }

    @Override public String description() {
        return "Get supplementary current telemetry and maintenance-state observations for the authorized vehicle.";
    }

    @Override
    public ToolOutcome execute(ToolExecutionContext context, String vehicleId) {
        context.requireVehicle(vehicleId);
        try {
            JsonNode state = telemetry.maintenanceState(vehicleId);
            ToolFactMapper.requireVehicle(state, vehicleId);
            Instant timestamp = state.hasNonNull("sourceTimestamp") ? Instant.parse(state.get("sourceTimestamp").asText()) : null;
            return ToolOutcome.success(name(), "Supplementary Telemetry maintenance-state retrieved", ToolFactMapper.facts(name(), state, vehicleId), List.of(new EvidenceSource(
                    "telemetry-state-" + vehicleId, "BUSINESS_API", "Telemetry", vehicleId, Instant.now(), timestamp, "Telemetry")));
        } catch (UpstreamException exception) {
            return ToolOutcome.failure(name(), exception.timeout() ? "TIMEOUT" : exception.status() == 404 ? "NOT_FOUND" : "UPSTREAM_UNAVAILABLE", exception.getMessage(), exception.timeout() || exception.status() >= 500);
        } catch (RuntimeException exception) {
            return ToolOutcome.failure(name(), "INVALID_UPSTREAM_RESPONSE", "Telemetry returned an invalid maintenance-state response", false);
        }
    }
}
