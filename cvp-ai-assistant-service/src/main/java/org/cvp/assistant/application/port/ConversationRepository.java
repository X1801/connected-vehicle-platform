package org.cvp.assistant.application.port;

import org.cvp.assistant.domain.conversation.Conversation;

import java.util.Optional;

public interface ConversationRepository {
    Optional<Conversation> findById(String id);
    Conversation save(Conversation conversation);
}
