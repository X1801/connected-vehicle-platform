package org.cvp.assistant.tool.inventory;

import tools.jackson.databind.JsonNode;
import org.cvp.assistant.client.inventory.InventoryClientPort;
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
public class GetVehicleCapabilityTool implements AssistantTool<String> {
    private final InventoryClientPort inventory;
    public GetVehicleCapabilityTool(InventoryClientPort inventory) { this.inventory = inventory; }
    @Override public String name() { return "getVehicleCapability"; }
    @Override public String description() { return "Get static vehicle model and capability context for the authorized vehicle."; }
    @Override public ToolOutcome execute(ToolExecutionContext context, String vehicleId) {
        context.requireVehicle(vehicleId);
        try {
            JsonNode capability = inventory.capability(vehicleId);
            ToolFactMapper.requireVehicle(capability, vehicleId);
            return ToolOutcome.success(name(), "Inventory capability retrieved", ToolFactMapper.facts(name(), capability, vehicleId), List.of(new EvidenceSource(
                    "inventory-capability-" + vehicleId, "LOCAL_MOCK", "Inventory Mock", vehicleId, Instant.now(), null, "LOCAL_MOCK")));
        } catch (UpstreamException exception) {
            return ToolOutcome.failure(name(), exception.timeout() ? "TIMEOUT" : exception.status() == 404 ? "NOT_FOUND" : "UPSTREAM_UNAVAILABLE", exception.getMessage(), exception.timeout() || exception.status() >= 500);
        }
    }
}
