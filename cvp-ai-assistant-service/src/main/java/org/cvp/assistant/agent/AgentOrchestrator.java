package org.cvp.assistant.agent;

import io.micrometer.core.instrument.MeterRegistry;
import org.cvp.assistant.application.chat.ChatCommand;
import org.cvp.assistant.application.chat.ChatResult;
import org.cvp.assistant.application.port.AgentPort;
import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.llm.LlmPort;
import org.cvp.assistant.llm.ToolCall;
import org.cvp.assistant.rag.RetrievalPort;
import org.cvp.assistant.rag.RagPassage;
import org.cvp.assistant.tool.ToolExecutionContext;
import org.cvp.assistant.tool.TypedToolRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AgentOrchestrator implements AgentPort {
    private final TypedToolRegistry tools;
    private final RetrievalPort rag;
    private final LlmPort llm;
    private final MeterRegistry meters;
    private final long timeoutSeconds;
    private final int maxRounds;

    public AgentOrchestrator(TypedToolRegistry tools, RetrievalPort rag, LlmPort llm, MeterRegistry meters,
                             @Value("${assistant.turn-timeout:20s}") java.time.Duration turnTimeout,
                             @Value("${assistant.tool.max-rounds:3}") int maxRounds) {
        this.tools = tools;
        this.rag = rag;
        this.llm = llm;
        this.meters = meters;
        this.timeoutSeconds = turnTimeout.toSeconds();
        this.maxRounds = maxRounds;
    }

    @Override
    public ChatResult answer(ChatCommand command, String conversationSummary) {
        ToolExecutionContext context = new ToolExecutionContext(command.principal(), command.vehicleId(), command.correlationId(),
                Instant.now().plusSeconds(timeoutSeconds));
        List<ToolOutcome> outcomes = new ArrayList<>();
        List<RagPassage> passages;
        try {
            passages = rag.retrieve(command.userMessage(), command.vehicleId());
        } catch (RuntimeException retrievalFailure) {
            passages = List.of();
            outcomes.add(ToolOutcome.failure("retrieveKnowledge", "RAG_UNAVAILABLE", "Knowledge retrieval is unavailable", true));
        }
        Set<String> selectedTools = new HashSet<>();
        boolean roundLimitExhausted = false;
        for (int round = 0; round < maxRounds; round++) {
            List<ToolCall> proposals = llm.selectTools(command.userMessage(), conversationSummary, List.copyOf(outcomes), passages, tools.definitions());
            if (proposals.isEmpty()) break;
            if (round == maxRounds - 1 && proposals.stream().anyMatch(proposal -> selectedTools.contains(proposal.name()))) {
                roundLimitExhausted = true;
            }
            for (ToolCall proposal : proposals) {
                selectedTools.add(proposal.name());
                if (proposal.invalidArguments()) {
                    outcomes.add(ToolOutcome.failure(proposal.name(), "INVALID_ARGUMENT", "Tool arguments must include the authorized vehicle identifier", false));
                } else if (!tools.isRegistered(proposal.name())) {
                    outcomes.add(ToolOutcome.failure(proposal.name(), "INVALID_ARGUMENT", "Tool is not registered", false));
                } else if (proposal.vehicleId() != null && !command.vehicleId().equals(proposal.vehicleId())) {
                    outcomes.add(ToolOutcome.failure(proposal.name(), "FORBIDDEN", "Tool vehicle does not match the authorized chat vehicle", false));
                } else {
                    outcomes.add(measured(proposal.name(), () -> tools.execute(proposal.name(), context, command.vehicleId())));
                }
            }
        }
        if (roundLimitExhausted)
            outcomes.add(ToolOutcome.failure("toolSelection", "ROUND_LIMIT_EXHAUSTED", "Repeated tool selection reached its configured round limit", false));
        if (outcomes.isEmpty() && AnswerCitationValidator.requiresBusinessEvidence(command.userMessage())) {
            outcomes.add(ToolOutcome.failure("toolSelection", "NO_TOOL_SELECTED", "No tool was selected to obtain required business evidence", false));
        }
        EvidenceBundle evidence = evidenceIds(outcomes, passages);
        long started = System.nanoTime();
        String generated = llm.composeAnswer(command.userMessage(), conversationSummary, evidence.outcomes(), evidence.passages());
        AnswerCitationValidator.Result validation = AnswerCitationValidator.validatedOrFallback(command.userMessage(), generated, evidence.outcomes(), evidence.passages());
        meters.timer("assistant.llm.duration").record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        return new ChatResult(command.conversationId(), validation.answer(), evidence.sources(), evidence.outcomes(), evidence.passages(), validation.citationIds(), validation.fallback(), null);
    }

    private ToolOutcome measured(String tool, java.util.function.Supplier<ToolOutcome> operation) {
        long started = System.nanoTime();
        ToolOutcome result = operation.get();
        meters.timer("assistant.tool.duration", "tool", tool).record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        if (result.status() != ToolOutcome.Status.SUCCESS) {
            meters.counter("assistant.tool.failures", "tool", tool, "code", result.error() == null ? "PARTIAL" : result.error().code()).increment();
        }
        return result;
    }

    private EvidenceBundle evidenceIds(List<ToolOutcome> outcomes, List<RagPassage> passages) {
        int[] next = {1, 1};
        List<EvidenceSource> sources = new ArrayList<>();
        List<ToolOutcome> rebound = new ArrayList<>();
        for (ToolOutcome outcome : outcomes) {
            List<EvidenceSource> mapped = outcome.sources().stream().map(source -> {
                if (outcome.status() != ToolOutcome.Status.SUCCESS) return source.withEvidenceId(null);
                EvidenceSource assigned = source.withEvidenceId("E" + next[0]++);
                sources.add(assigned);
                return assigned;
            }).toList();
            rebound.add(new ToolOutcome(outcome.tool(), outcome.status(), outcome.summary(), outcome.facts(), mapped, outcome.error()));
        }
        List<RagPassage> mappedPassages = passages.stream().map(passage -> {
            EvidenceSource assigned = passage.source().withEvidenceId("R" + next[1]++);
            sources.add(assigned);
            return new RagPassage(assigned, passage.text());
        }).toList();
        return new EvidenceBundle(List.copyOf(rebound), List.copyOf(mappedPassages), List.copyOf(sources));
    }

    private record EvidenceBundle(List<ToolOutcome> outcomes, List<RagPassage> passages, List<EvidenceSource> sources) {
    }
}
