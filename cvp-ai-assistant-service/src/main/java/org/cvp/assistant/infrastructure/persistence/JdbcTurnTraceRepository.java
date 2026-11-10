package org.cvp.assistant.infrastructure.persistence;

import org.cvp.assistant.application.chat.*;
import org.cvp.assistant.domain.evidence.*;
import org.cvp.assistant.rag.RagPassage;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository @Profile("persistent")
public class JdbcTurnTraceRepository implements TurnTraceRepository {
    private final JdbcTemplate jdbc; public JdbcTurnTraceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean durable() { return true; }
    public void save(UUID turnId, ChatCommand command, ChatResult result) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO conversation_turn(turn_id,conversation_id,user_message,assistant_message,correlation_id,answer_classification,created_at) VALUES (?,?,?,?,?,?,?)", turnId, result.conversationId(), cut(command.userMessage(), 4000), cut(result.answer(), 12000), command.correlationId(), result.fallback() ? "SAFE_FALLBACK" : "ANSWER", Timestamp.from(now));
        for (ToolOutcome outcome : result.toolResults()) saveTool(turnId, outcome, result.citationIds(), now);
        for (RagPassage passage : result.ragPassages()) saveRag(turnId, passage, result.citationIds(), now);
        for (String citation : result.citationIds()) jdbc.update("INSERT INTO conversation_turn_citation(turn_id,evidence_id) VALUES (?,?)", turnId, citation);
    }
    private void saveTool(UUID turnId, ToolOutcome outcome, List<String> cited, Instant now) {
        List<EvidenceSource> sources = outcome.sources().isEmpty() ? Collections.singletonList(null) : outcome.sources();
        boolean retrievalStatus = outcome.tool().equals("retrieveKnowledge");
        for (EvidenceSource source : sources) {
            boolean citable = source != null && outcome.status() == ToolOutcome.Status.SUCCESS;
            insert(turnId, source, retrievalStatus ? "RAG_STATUS" : "TOOL", citable && cited.contains(source.evidenceId()), citable,
                    retrievalStatus ? null : UUID.randomUUID(), outcome.tool(), executionPath(source), outcome.status().name(),
                    outcome.error() == null ? null : outcome.error().code(), cut(outcome.facts(), 8000), null, now);
        }
    }
    private void saveRag(UUID turnId, RagPassage passage, List<String> cited, Instant now) {
        insert(turnId, passage.source(), "RAG", cited.contains(passage.source().evidenceId()), true, null, null, null, "RETRIEVED", null, null, cut(passage.text(), 4000), now);
    }
    private void insert(UUID turnId, EvidenceSource source, String kind, boolean cited, boolean citable, UUID execution, String tool, String path, String status, String error, String facts, String excerpt, Instant now) {
        jdbc.update("INSERT INTO conversation_turn_evidence(id,turn_id,evidence_id,kind,citable,cited,tool_execution_id,tool_name,execution_path,status,error_code,facts,source_id,source_type,source_service,resource_id,source_timestamp,retrieved_at,provenance,excerpt,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), turnId, source == null ? null : source.evidenceId(), kind, citable, cited, execution, tool, path, status, error, facts, source == null ? null : source.sourceId(), source == null ? null : source.type(), source == null ? null : source.service(), source == null ? null : source.resourceId(), source == null || source.sourceTimestamp() == null ? null : Timestamp.from(source.sourceTimestamp()), source == null ? null : Timestamp.from(source.retrievedAt()), source == null ? null : source.provenance(), excerpt, Timestamp.from(now));
    }
    public Optional<Map<String, Object>> find(String conversationId, UUID turnId) {
        List<Map<String, Object>> turns = jdbc.queryForList("SELECT turn_id, assistant_message, created_at, answer_classification FROM conversation_turn WHERE conversation_id=? AND turn_id=?", conversationId, turnId);
        if (turns.isEmpty()) return Optional.empty();
        Map<String, Object> trace = new LinkedHashMap<>();
        Map<String, Object> turn = turns.getFirst();
        trace.put("conversationId", conversationId);
        trace.put("turnId", String.valueOf(turn.get("turn_id")));
        trace.put("answer", turn.get("assistant_message"));
        trace.put("createdAt", turn.get("created_at"));
        trace.put("classification", turn.get("answer_classification"));
        trace.put("citationIds", jdbc.queryForList("SELECT evidence_id FROM conversation_turn_citation WHERE turn_id=? ORDER BY evidence_id", String.class, turnId));
        trace.put("evidence", jdbc.queryForList("SELECT evidence_id, kind, citable, cited, tool_execution_id, tool_name, execution_path, status, error_code, facts, source_id, source_type, source_service, resource_id, source_timestamp, retrieved_at, provenance, excerpt FROM conversation_turn_evidence WHERE turn_id=? ORDER BY created_at, id", turnId));
        return Optional.of(trace);
    }
    private String cut(String value, int max) { return value == null ? null : value.length() <= max ? value : value.substring(0, max); }
    private String executionPath(EvidenceSource source) { return source == null ? null : "MCP".equals(source.provenance()) ? "MCP" : "DIRECT"; }
}
