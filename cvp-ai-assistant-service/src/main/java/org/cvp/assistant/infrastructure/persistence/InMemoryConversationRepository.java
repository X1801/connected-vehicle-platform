package org.cvp.assistant.infrastructure.persistence;

import org.cvp.assistant.application.port.ConversationRepository;
import org.cvp.assistant.domain.conversation.Conversation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!persistent")
public class InMemoryConversationRepository implements ConversationRepository {
    private final ConcurrentHashMap<String, Conversation> conversations = new ConcurrentHashMap<>();

    @Override
    public Optional<Conversation> findById(String id) {
        return Optional.ofNullable(conversations.get(id));
    }

    @Override
    public Conversation save(Conversation conversation) {
        conversations.compute(conversation.id(), (id, existing) -> {
            if (existing != null && existing.version() >= conversation.version()) {
                throw new IllegalStateException("Conversation was updated concurrently");
            }
            return conversation;
        });
        return conversation;
    }
}
