package org.cvp.mcp.config;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.cvp.mcp.VehicleTools;
import org.cvp.mcp.tool.McpToolResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Registers structured official MCP tool results, never raw business text. */
@Configuration
public class McpToolConfiguration {
    private static final McpSchema.JsonSchema VEHICLE_ARGUMENT = new McpSchema.JsonSchema(
            "object", Map.of("vehicleId", Map.of("type", "string", "description", "Authorized vehicle identifier")),
            List.of("vehicleId"), false, null, null);

    @Bean
    List<McpServerFeatures.SyncToolSpecification> vehicleToolSpecifications(VehicleTools tools) {
        return List.of(
                specification("getMaintenanceStatus", "Get authoritative maintenance reminder service maintenance status and reminders for the authorized vehicle.", tools::getMaintenanceStatus),
                specification("getVehicleMaintenanceState", "Get supplementary current Telemetry telemetry and maintenance state for the authorized vehicle.", tools::getVehicleMaintenanceState),
                specification("getVehicleCapability", "Get Inventory vehicle capability and model context for the authorized vehicle.", tools::getVehicleCapability));
    }

    private McpServerFeatures.SyncToolSpecification specification(String name, String description,
                                                                    Function<String, McpToolResponse> operation) {
        McpSchema.Tool tool = new McpSchema.Tool(name, null, description, VEHICLE_ARGUMENT, null, null, null);
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, arguments) -> {
            Object vehicle = arguments.get("vehicleId");
            McpToolResponse result = operation.apply(vehicle instanceof String value ? value : null);
            Map<String, Object> payload = result.status().equals("SUCCESS")
                    ? Map.of("status", "SUCCESS", "data", result.data())
                    : Map.of("status", "ERROR", "source", result.source(), "error", Map.of(
                            "code", result.error().code(), "message", result.error().message(), "retryable", result.error().retryable()));
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(result.status().equals("SUCCESS")
                    ? "Structured business result available." : "Business tool failed.")), !result.status().equals("SUCCESS"), payload);
        });
    }
}
