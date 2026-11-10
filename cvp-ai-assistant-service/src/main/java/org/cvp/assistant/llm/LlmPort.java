package org.cvp.assistant.llm;

import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.rag.RagPassage;
import org.cvp.assistant.tool.ToolDefinition;

import java.util.List;

public interface LlmPort {
    List<ToolCall> selectTools(String question, String conversationSummary, List<ToolOutcome> evidence,
                               List<RagPassage> ragPassages, List<ToolDefinition> availableTools);
    String composeAnswer(String question, String conversationSummary, List<ToolOutcome> evidence, List<RagPassage> ragPassages);
}
