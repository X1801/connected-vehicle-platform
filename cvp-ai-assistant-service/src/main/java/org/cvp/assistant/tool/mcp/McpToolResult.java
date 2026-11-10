package org.cvp.assistant.tool.mcp;

import java.util.Map;

record McpToolResult(boolean error, Map<String, Object> payload) { }
