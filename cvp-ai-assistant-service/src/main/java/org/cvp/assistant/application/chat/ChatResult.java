package org.cvp.assistant.application.chat;

import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;

import java.util.List;
import org.cvp.assistant.rag.RagPassage;

public record ChatResult(String conversationId, String answer, List<EvidenceSource> sources, List<ToolOutcome> toolResults,
                         List<RagPassage> ragPassages, List<String> citationIds, boolean fallback, String turnId) {
    public ChatResult(String conversationId, String answer, List<EvidenceSource> sources, List<ToolOutcome> toolResults) {
        this(conversationId, answer, sources, toolResults, List.of(), List.of(), false, null);
    }
}
