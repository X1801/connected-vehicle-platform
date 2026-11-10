package org.cvp.assistant.tool;

import java.util.Map;

/** Function-calling metadata exposed to the LLM; execution remains internal. */
public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) { }
