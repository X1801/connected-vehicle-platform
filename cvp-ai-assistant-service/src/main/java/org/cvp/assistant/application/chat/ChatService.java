package org.cvp.assistant.application.chat;

import org.cvp.assistant.application.port.AgentPort;
import org.cvp.assistant.application.port.ConversationRepository;
import org.cvp.assistant.application.port.VehicleAccessPort;
import org.cvp.assistant.domain.conversation.Conversation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatService implements ChatUseCase {
    private final ConversationRepository conversations;
    private final VehicleAccessPort vehicleAccess;
    private final AgentPort agent;
    private final TurnTraceService traces;

    @Autowired
    public ChatService(ConversationRepository conversations, VehicleAccessPort vehicleAccess, AgentPort agent, TurnTraceService traces) {
        this.conversations = conversations;
        this.vehicleAccess = vehicleAccess;
        this.agent = agent;
        this.traces = traces;
    }

    /** Retains the original construction seam when durable persistence is not configured. */
    public ChatService(ConversationRepository conversations, VehicleAccessPort vehicleAccess, AgentPort agent) {
        this(conversations, vehicleAccess, agent, new TurnTraceService(conversations, new TurnTraceRepository() {
            @Override public boolean durable() { return false; }
            @Override public void save(UUID turnId, ChatCommand command, ChatResult result) { }
            @Override public Optional<Map<String, Object>> find(String conversationId, UUID turnId) { return Optional.empty(); }
        }));
    }

    @Override
    public ChatResult chat(ChatCommand command) {
        if (!vehicleAccess.canAccess(command.principal(), command.vehicleId())) {
            throw new ForbiddenException("Vehicle access was denied");
        }
        Conversation conversation = loadOrCreate(command);
        ChatResult result = agent.answer(command, conversation.summary());
        var next = conversation.next(summarize(command.userMessage(), result.answer()));
        UUID savedTurnId = traces.save(next, command, new ChatResult(conversation.id(), result.answer(), result.sources(), result.toolResults(), result.ragPassages(), result.citationIds(), result.fallback(), null));
        String turnId = savedTurnId == null ? null : savedTurnId.toString();
        return new ChatResult(conversation.id(), result.answer(), result.sources(), result.toolResults(), result.ragPassages(), result.citationIds(), result.fallback(), turnId);
    }

    private Conversation loadOrCreate(ChatCommand command) {
        if (command.conversationId() == null || command.conversationId().isBlank()) {
            return new Conversation(UUID.randomUUID().toString(), command.principal(), command.vehicleId(), "", 0, Instant.now());
        }
        Conversation found = conversations.findById(command.conversationId())
                .orElseThrow(() -> new NotFoundException("Conversation was not found"));
        if (!found.principal().equals(command.principal()) || !found.vehicleId().equals(command.vehicleId())) {
            throw new NotFoundException("Conversation was not found");
        }
        return found;
    }

    private String summarize(String question, String answer) {
        String value = "User: " + question + " Assistant: " + answer;
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
