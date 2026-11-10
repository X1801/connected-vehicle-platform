package org.cvp.mcp;

import org.cvp.mcp.client.DownstreamClient;
import org.cvp.mcp.tool.McpToolResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Exposes integration-only domain capabilities; no maintenance decisions are recalculated here. */
@Component
public class VehicleTools {
    private final DownstreamClient maintenanceReminder;
    private final DownstreamClient telemetry;
    private final DownstreamClient inventory;
    public VehicleTools(DownstreamClient maintenanceReminderClient, DownstreamClient telemetryClient, DownstreamClient inventoryClient) {
        this.maintenanceReminder = maintenanceReminderClient.named("maintenance-reminder"); this.telemetry = telemetryClient.named("telemetry"); this.inventory = inventoryClient.named("inventory");
    }
    @Tool(description = "Get authoritative maintenance status and reminders for the authorized vehicle.")
    public McpToolResponse getMaintenanceStatus(@ToolParam(description = "Authorized vehicle identifier") String vehicleId) {
        return maintenanceReminder.getMaintenance(vehicleId);
    }
    @Tool(description = "Get supplementary current Telemetry telemetry and maintenance state for the authorized vehicle.")
    public McpToolResponse getVehicleMaintenanceState(@ToolParam(description = "Authorized vehicle identifier") String vehicleId) {
        return telemetry.get("/v1/vehicles/{vehicleId}/maintenance-state", vehicleId);
    }
    @Tool(description = "Get Inventory vehicle capability and model context for the authorized vehicle.")
    public McpToolResponse getVehicleCapability(@ToolParam(description = "Authorized vehicle identifier") String vehicleId) {
        return inventory.get("/v1/vehicles/{vehicleId}/capability", vehicleId);
    }
}
