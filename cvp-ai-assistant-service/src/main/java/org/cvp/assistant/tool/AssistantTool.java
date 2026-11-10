package org.cvp.assistant.tool;

import org.cvp.assistant.domain.evidence.ToolOutcome;

public interface AssistantTool<I> {
    String name();
    String description();
    ToolOutcome execute(ToolExecutionContext context, I input);
}
