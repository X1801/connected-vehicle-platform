package org.cvp.assistant.tool;

import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Internal policy gate; this is intentionally not a network service or MCP server.
 */
@Component
public class TypedToolRegistry {
    private final Map<String, AssistantTool<String>> tools;

    public TypedToolRegistry(java.util.List<AssistantTool<String>> registeredTools) {
        this.tools = registeredTools.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(AssistantTool::name, tool -> tool));
    }

    public ToolOutcome execute(String name, ToolExecutionContext context, String input) {
        AssistantTool<String> tool = tools.get(name);
        if (tool == null) {
            return ToolOutcome.failure(name, "INVALID_ARGUMENT", "Tool is not registered", false);
        }
        try {
            return tool.execute(context, input);
        } catch (IllegalArgumentException exception) {
            return ToolOutcome.failure(name, "INVALID_ARGUMENT", exception.getMessage(), false);
        } catch (ToolDeadlineExceededException exception) {
            return ToolOutcome.failure(name, "TIMEOUT", exception.getMessage(), true);
        }
    }

    public boolean isRegistered(String name) {
        return definitions().stream().anyMatch(definition -> definition.name().equals(name));
    }

    public java.util.List<ToolDefinition> definitions() {
        return tools.values().stream()
                // Reminder detail needs a reminder ID, not the chat's vehicle-scoped input contract.
                // Do not expose it until it has a separately validated input definition.
                .filter(tool -> !tool.name().equals("getReminderDetail"))
                .map(tool -> new ToolDefinition(tool.name(), tool.description(), Map.of(
                        "type", "object",
                        "properties", Map.of("vehicleId", Map.of("type", "string",
                                "description", "Optional vehicle identifier. If supplied, it must match the authorized chat vehicle.")),
                        "additionalProperties", false)))
                .sorted(java.util.Comparator.comparing(ToolDefinition::name))
                .toList();
    }
}
