package org.cvp.assistant.llm;

import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.rag.RagPassage;

import java.util.List;
import java.util.stream.Collectors;

/** Builds a bounded, provenance-labelled prompt payload from successful evidence only. */
public final class LlmContextBuilder {
    private LlmContextBuilder() { }

    public static String userMessage(String question, String conversationSummary, List<ToolOutcome> outcomes, List<RagPassage> passages) {
        String toolFacts = outcomes.stream().filter(outcome -> outcome.status() == ToolOutcome.Status.SUCCESS)
                .map(LlmContextBuilder::toolBlock).collect(Collectors.joining("\n---\n"));
        String availability = outcomes.stream().filter(outcome -> outcome.status() != ToolOutcome.Status.SUCCESS)
                .map(outcome -> outcome.tool() + "=" + (outcome.error() == null ? "UNAVAILABLE" : outcome.error().code()))
                .collect(Collectors.joining(", "));
        String knowledge = passages.stream().map(LlmContextBuilder::ragBlock).collect(Collectors.joining("\n---\n"));
        return "Question: " + question + "\nConversation context: " + conversationSummary
                + "\nCitation rule: cite factual claims only as [E1], [E2], etc. or [R1], [R2], etc. using IDs shown in supplied evidence. Never invent source names, citation IDs, or tool executions."
                + "\nTool results/current vehicle facts:\n" + toolFacts
                + "\nUnavailable or unverified sources (not factual evidence and never citable):\n" + availability
                + "\nRAG knowledge/rules:\n" + knowledge;
    }

    private static String toolBlock(ToolOutcome outcome) {
        return "[" + outcome.tool() + " | " + sources(outcome.sources()) + "]\n" + outcome.facts();
    }

    private static String ragBlock(RagPassage passage) {
        EvidenceSource source = passage.source();
        return "[" + source.evidenceId() + " | RAG | source=" + source.resourceId() + " | provenance=" + source.provenance()
                + " | chunk=" + source.sourceId() + "]\n" + passage.text();
    }

    private static String sources(List<EvidenceSource> sources) {
        return sources.stream().map(source -> source.evidenceId() + ":" + source.service() + ":" + source.provenance()).collect(Collectors.joining(", "));
    }
}
