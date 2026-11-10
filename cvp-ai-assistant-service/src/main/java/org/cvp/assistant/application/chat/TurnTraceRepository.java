package org.cvp.assistant.application.chat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TurnTraceRepository {
    /** True only when a completed turn can be retrieved durably. */
    boolean durable();
    void save(UUID turnId, ChatCommand command, ChatResult result);
    Optional<Map<String, Object>> find(String conversationId, UUID turnId);
}
