package org.cvp.assistant.tool.mcp;

import java.util.Map;
import java.util.Set;

/** Boundary around the official MCP SDK client, kept small for deterministic adapter tests. */
interface McpClientSession extends AutoCloseable {
    void initialize();
    Set<String> listToolNames();
    McpToolResult callTool(String name, Map<String, Object> arguments);
    @Override void close();
}
