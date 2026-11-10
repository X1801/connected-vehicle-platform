package org.cvp.assistant.tool.mcp;

import org.cvp.assistant.tool.AssistantTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "assistant.tools", name = "transport", havingValue = "mcp")
public class McpToolConfiguration {
    @Bean
    AssistantTool<String> mcpMaintenanceStatusTool(McpProtocolClient c) {
        return new McpAssistantTool("getMaintenanceStatus", "Get authoritative maintenance status and scheduled reminders for the authorized vehicle.", "MAINTENANCE_REMINDER", c);
    }

    @Bean
    AssistantTool<String> mcpTelemetryStateTool(McpProtocolClient c) {
        return new McpAssistantTool("getVehicleMaintenanceState", "Get supplementary current telemetry and maintenance-state observations for the authorized vehicle.", "Telemetry", c);
    }

    @Bean
    AssistantTool<String> mcpInventoryCapabilityTool(McpProtocolClient c) {
        return new McpAssistantTool("getVehicleCapability", "Get static vehicle model and capability context for the authorized vehicle.", "Inventory", c);
    }
}
