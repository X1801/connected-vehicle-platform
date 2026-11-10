package org.cvp.assistant.infrastructure.persistence;

import org.cvp.assistant.application.port.ConversationRepository;
import org.cvp.assistant.domain.conversation.Conversation;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

/** Conversation records belong to the Assistant database, separate from the maintenance service database. */
// Replaced by JpaConversationRepository for persistent deployments.
@Profile("jdbc-conversation")
public class JdbcConversationRepository implements ConversationRepository {
    private final JdbcTemplate jdbc;

    public JdbcConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Conversation> findById(String id) {
        return jdbc.query("SELECT id, principal, vehicle_id, summary, version, updated_at FROM conversation WHERE id = ?",
                rs -> rs.next() ? Optional.of(new Conversation(rs.getString("id"), rs.getString("principal"),
                        rs.getString("vehicle_id"), rs.getString("summary"), rs.getLong("version"),
                        rs.getTimestamp("updated_at").toInstant())) : Optional.empty(), id);
    }

    @Override
    public Conversation save(Conversation conversation) {
        jdbc.update("""
                INSERT INTO conversation (id, principal, vehicle_id, summary, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET summary = EXCLUDED.summary, version = EXCLUDED.version,
                    updated_at = EXCLUDED.updated_at
                WHERE conversation.version < EXCLUDED.version
                """, conversation.id(), conversation.principal(), conversation.vehicleId(), conversation.summary(),
                conversation.version(), Timestamp.from(conversation.updatedAt()));
        return conversation;
    }
}
