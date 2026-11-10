package org.cvp.assistant.infrastructure.persistence;

import org.cvp.assistant.application.chat.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository @Profile("!persistent")
public class NoopTurnTraceRepository implements TurnTraceRepository {
    public boolean durable() { return false; }
    public void save(UUID turnId, ChatCommand command, ChatResult result) { }
    public Optional<Map<String, Object>> find(String conversationId, UUID turnId) { return Optional.empty(); }
}
