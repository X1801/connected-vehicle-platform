package org.cvp.assistant.tool.mcp;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

/**
 * MCP adapter using the official Java SDK for Streamable HTTP protocol and session handling.
 */
@Component
public class McpProtocolClient {
    private static final Set<String> ALLOWED = Set.of("getMaintenanceStatus", "getVehicleMaintenanceState", "getVehicleCapability");
    private final McpClientSessionFactory sessionFactory;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private McpClientSession session;
    private Set<String> discovered = Set.of();

    McpProtocolClient(McpClientSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public synchronized JsonNode call(String tool, String vehicleId, String correlationId) {
        if (!ALLOWED.contains(tool))
            throw new McpToolException("INVALID_ARGUMENT", "MCP tool is not allowlisted", false);
        ensureSession();
        if (!discovered.contains(tool))
            throw new McpToolException("MCP_UNAVAILABLE", "Configured MCP tool is unavailable", true);
        try {
            McpToolResult result = session.callTool(tool, Map.of("vehicleId", vehicleId));
            if (result.error())
                throw new McpToolException("UPSTREAM_UNAVAILABLE", "MCP downstream invocation failed", true);
            return jsonMapper.readTree(jsonMapper.writeValueAsString(result.payload()));
        } catch (McpToolException e) {
            throw e;
        } catch (RuntimeException e) {
            resetSession();
            throw new McpToolException("MCP_UNAVAILABLE", "MCP server is unavailable", true);
        }
    }

    private void ensureSession() {
        if (session != null) return;
        try {
            McpClientSession candidate = sessionFactory.open();
            candidate.initialize();
            Set<String> names = candidate.listToolNames();
            if (!names.containsAll(ALLOWED)) {
                candidate.close();
                throw new McpToolException("MCP_UNAVAILABLE", "MCP server is missing required allowlisted tools", true);
            }
            session = candidate;
            discovered = Set.copyOf(names);
        } catch (McpToolException e) {
            throw e;
        } catch (RuntimeException e) {
            resetSession();
            throw new McpToolException("MCP_UNAVAILABLE", "MCP initialization failed", true);
        }
    }

    private void resetSession() {
        if (session != null) try {
            session.close();
        } catch (RuntimeException ignored) {
        }
        session = null;
        discovered = Set.of();
    }

    @PreDestroy
    void close() {
        resetSession();
    }
}
