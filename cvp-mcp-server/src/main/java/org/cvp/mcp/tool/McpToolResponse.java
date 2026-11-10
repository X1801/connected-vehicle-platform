package org.cvp.mcp.tool;

import java.util.Map;

/**
 * Deterministic, controlled tool payload returned to MCP clients.
 */
public record McpToolResponse(String status, String source, Map<String, Object> data, McpError error) {
    public static McpToolResponse success(String source, Map<String, Object> data) {
        return new McpToolResponse("SUCCESS", source, data, null);
    }

    public static McpToolResponse failure(String source, String code, String message, boolean retryable) {
        return new McpToolResponse("ERROR", source, Map.of(), new McpError(code, message, retryable));
    }

    public record McpError(String code, String message, boolean retryable) {
    }
}
