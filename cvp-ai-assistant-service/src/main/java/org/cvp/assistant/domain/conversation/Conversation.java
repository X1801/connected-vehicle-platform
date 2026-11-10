package org.cvp.assistant.domain.conversation;

import java.time.Instant;

public record Conversation(String id, String principal, String vehicleId, String summary, long version, Instant updatedAt) {
    public Conversation next(String nextSummary) {
        return new Conversation(id, principal, vehicleId, nextSummary, version + 1, Instant.now());
    }
}
