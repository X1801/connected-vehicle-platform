package org.cvp.assistant.controller.chat;

import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;

import java.util.List;

public record ChatResponse(String conversationId, String answer, List<EvidenceSource> sources, List<ToolOutcome> toolResults, String turnId) {
}
