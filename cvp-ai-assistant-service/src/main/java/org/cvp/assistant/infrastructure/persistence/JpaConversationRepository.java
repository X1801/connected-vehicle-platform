package org.cvp.assistant.infrastructure.persistence;

import org.cvp.assistant.application.port.ConversationRepository;
import org.cvp.assistant.domain.conversation.Conversation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Profile("persistent")
public class JpaConversationRepository implements ConversationRepository {
    private final SpringDataConversationRepository repo;

    public JpaConversationRepository(SpringDataConversationRepository repo) {
        this.repo = repo;
    }

    public Optional<Conversation> findById(String id) {
        return repo.findById(id).map(e -> new Conversation(e.id(), e.principal(), e.vehicleId(), e.summary(), e.version(), e.updatedAt()));
    }

    public Conversation save(Conversation c) {
        ConversationEntity e = repo.findById(c.id()).orElseGet(() -> new ConversationEntity(c.id(), c.principal(), c.vehicleId(), c.summary(), 0, c.updatedAt()));
        if (!e.summary().equals(c.summary())) e.update(c.summary(), c.updatedAt());
        e = repo.saveAndFlush(e);
        return new Conversation(e.id(), e.principal(), e.vehicleId(), e.summary(), e.version(), e.updatedAt());
    }
}
