package org.cvp.assistant.integration;

import org.cvp.assistant.application.chat.ChatCommand;
import org.cvp.assistant.application.chat.ChatResult;
import org.cvp.assistant.application.chat.TurnTraceService;
import org.cvp.assistant.application.port.ConversationRepository;
import org.cvp.assistant.domain.conversation.Conversation;
import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the production JPA conversation repository and JDBC trace repository share one Spring transaction. */
@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"assistant.rag.ingest-on-startup=false", "assistant.deepseek.enabled=false"})
@ActiveProfiles("persistent")
@Testcontainers
class TurnTraceTransactionIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TurnTraceService traces;
    @Autowired ConversationRepository conversations;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rollsBackJpaConversationAndJdbcTraceWhenCitationWriteFailsThenCommitsValidTrace() {
        Conversation failedConversation = conversation("trace-rollback");
        ChatResult invalidCitation = new ChatResult(failedConversation.id(), "answer [E404]", List.of(), List.of(), List.of(), List.of("E404"), true, null);

        assertThrows(RuntimeException.class, () -> traces.save(failedConversation, command(failedConversation.id()), invalidCitation));
        assertTrue(conversations.findById(failedConversation.id()).isEmpty());
        assertEquals(0, count("conversation_turn"));
        assertEquals(0, count("conversation_turn_evidence"));
        assertEquals(0, count("conversation_turn_citation"));

        Conversation successfulConversation = conversation("trace-success");
        EvidenceSource source = new EvidenceSource("maintenance-reminder-trace-success", "BUSINESS_API", "MAINTENANCE_REMINDER", "TEST-VEHICLE", Instant.now(), null, "DIRECT", "E1");
        ToolOutcome outcome = ToolOutcome.success("getMaintenanceStatus", "ok", "status=PENDING", List.of(source));
        ChatResult valid = new ChatResult(successfulConversation.id(), "Verified pending [E1]", List.of(source), List.of(outcome), List.of(), List.of("E1"), false, null);

        var turnId = traces.save(successfulConversation, command(successfulConversation.id()), valid);
        assertTrue(conversations.findById(successfulConversation.id()).isPresent());
        assertEquals(1, count("conversation_turn"));
        assertEquals(1, count("conversation_turn_evidence"));
        assertEquals(1, count("conversation_turn_citation"));
        assertEquals("E1", jdbc.queryForObject("SELECT evidence_id FROM conversation_turn_citation WHERE turn_id=?", String.class, turnId));
    }

    private Conversation conversation(String id) { return new Conversation(id, "principal-a", "TEST-VEHICLE", "summary", 0, Instant.now()); }
    private ChatCommand command(String conversationId) { return new ChatCommand("principal-a", "TEST-VEHICLE", "question", conversationId, "correlation"); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
}
