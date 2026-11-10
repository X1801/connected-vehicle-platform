package org.cvp.assistant.tool.mcp;

import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.tool.AssistantTool;
import org.cvp.assistant.tool.ToolExecutionContext;
import org.cvp.assistant.tool.ToolFactMapper;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

final class McpAssistantTool implements AssistantTool<String> {
    private final String name, description, service;
    private final McpProtocolClient client;

    McpAssistantTool(String name, String description, String service, McpProtocolClient client) {
        this.name = name;
        this.description = description;
        this.service = service;
        this.client = client;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ToolOutcome execute(ToolExecutionContext context, String ignored) {
        context.requireVehicle(context.vehicleId());
        try {
            JsonNode result = client.call(name, context.vehicleId(), context.correlationId());
            JsonNode data = businessData(result);
            String facts = ToolFactMapper.facts(name, data, context.vehicleId());
            return ToolOutcome.success(name, service + " evidence retrieved through MCP", facts, List.of(new EvidenceSource("mcp-" + name + "-" + context.vehicleId(), "BUSINESS_API", service, context.vehicleId(), Instant.now(), sourceTimestamp(data), "MCP")));
        } catch (McpToolException e) {
            return ToolOutcome.failure(name, e.code(), e.getMessage(), e.retryable());
        } catch (IllegalArgumentException e) {
            return ToolOutcome.failure(name, "INVALID_UPSTREAM_RESPONSE", "MCP returned an invalid or unauthorized business payload", false);
        } catch (RuntimeException e) {
            return ToolOutcome.failure(name, "INVALID_UPSTREAM_RESPONSE", "MCP returned an invalid business payload", false);
        }
    }

    /** Accept only MCP structured business data; plain text is never vehicle evidence. */
    private JsonNode businessData(JsonNode result) {
        if ("SUCCESS".equals(result.path("status").asText("")) && result.path("data").isObject()) return result.path("data");
        JsonNode structured = result.path("structuredContent");
        if (structured.isObject() && "SUCCESS".equals(structured.path("status").asText("")) && structured.path("data").isObject()) {
            return structured.path("data");
        }
        throw new McpToolException("INVALID_UPSTREAM_RESPONSE", "MCP returned no valid structured business payload", false);
    }

    private Instant sourceTimestamp(JsonNode result) {
        return result.hasNonNull("sourceTimestamp") ? Instant.parse(result.get("sourceTimestamp").asText()) : null;
    }
}
