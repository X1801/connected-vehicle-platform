package org.cvp.assistant.application.chat;

import org.cvp.assistant.application.port.ConversationRepository;
import org.cvp.assistant.domain.conversation.Conversation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TurnTraceService {
    private final ConversationRepository conversations; private final TurnTraceRepository traces;
    public TurnTraceService(ConversationRepository conversations, TurnTraceRepository traces) { this.conversations = conversations; this.traces = traces; }
    @Transactional public UUID save(Conversation next, ChatCommand command, ChatResult result) {
        conversations.save(next);
        if (!traces.durable()) return null;
        UUID turnId = UUID.randomUUID();
        traces.save(turnId, command, result);
        return turnId;
    }
    @Transactional(readOnly = true) public Map<String, Object> findAuthorized(String conversationId, UUID turnId, String principal) {
        Conversation conversation = conversations.findById(conversationId).orElseThrow(() -> new NotFoundException("Conversation was not found"));
        if (!conversation.principal().equals(principal)) throw new NotFoundException("Conversation was not found");
        return traces.find(conversationId, turnId).orElseThrow(() -> new NotFoundException("Turn was not found"));
    }
}
