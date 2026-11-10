package org.cvp.assistant.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises V1/V2-to-V3 against legacy conversation and RAG rows in a disposable database. */
class FlywayConversationEvidenceMigrationIT {
    @Test
    void migratesPreExistingConversationAndRagDataToV3WithoutDataLoss() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            assertEquals(2, flyway(postgres, "2").migrate().migrationsExecuted);

            UUID documentId = UUID.randomUUID();
            UUID chunkId = UUID.randomUUID();
            try (Connection connection = connection(postgres)) {
                update(connection, "INSERT INTO conversation(id, principal, vehicle_id, summary, version, updated_at) VALUES ('existing-conversation', 'principal-a', 'TEST-VEHICLE', 'existing summary', 4, '2026-01-01T00:00:00Z')");
                update(connection, "INSERT INTO conversation_turn(conversation_id, user_message, assistant_message, created_at) VALUES ('existing-conversation', 'old question', 'old answer', '2026-01-01T00:00:01Z')");
                update(connection, "INSERT INTO rag_document(id, source_name, source_path, content_checksum, metadata, created_at, updated_at) VALUES ('" + documentId + "', 'legacy.md', '/knowledge/legacy.md', '" + "a".repeat(64) + "', '{}'::jsonb, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
                update(connection, "INSERT INTO rag_chunk(id, document_id, chunk_index, chunk_text, metadata, embedding, created_at, updated_at) VALUES ('" + chunkId + "', '" + documentId + "', 0, 'retained chunk', '{}'::jsonb, ARRAY[0.1, 0.2]::double precision[], '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
            }

            assertEquals(1, flyway(postgres, null).migrate().migrationsExecuted);

            UUID turnId = UUID.randomUUID();
            try (Connection connection = connection(postgres)) {
                assertEquals("existing summary", scalar(connection, "SELECT summary FROM conversation WHERE id = 'existing-conversation'"));
                assertEquals("retained chunk", scalar(connection, "SELECT chunk_text FROM rag_chunk WHERE id = '" + chunkId + "'"));
                update(connection, "INSERT INTO conversation_turn(turn_id, conversation_id, user_message, assistant_message, correlation_id, answer_classification, created_at) VALUES ('" + turnId + "', 'existing-conversation', 'new question', 'new answer', 'corr', 'ANSWER', '2026-01-01T00:00:02Z')");
                update(connection, "INSERT INTO conversation_turn_evidence(id, turn_id, evidence_id, kind, citable, cited) VALUES ('" + UUID.randomUUID() + "', '" + turnId + "', 'E1', 'TOOL', true, true)");
                update(connection, "INSERT INTO conversation_turn_citation(turn_id, evidence_id) VALUES ('" + turnId + "', 'E1')");
                assertThrows(Exception.class, () -> update(connection, "INSERT INTO conversation_turn_citation(turn_id, evidence_id) VALUES ('" + turnId + "', 'E999')"));
                assertEquals("E1", scalar(connection, "SELECT evidence_id FROM conversation_turn_citation WHERE turn_id = '" + turnId + "'"));
            }
        }
    }

    private Flyway flyway(PostgreSQLContainer<?> postgres, String target) {
        var configuration = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
    private Connection connection(PostgreSQLContainer<?> postgres) throws Exception { return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); }
    private void update(Connection connection, String sql) throws Exception { try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.executeUpdate(); } }
    private String scalar(Connection connection, String sql) throws Exception { try (ResultSet result = connection.createStatement().executeQuery(sql)) { result.next(); return result.getString(1); } }
}
