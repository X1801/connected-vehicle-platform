package org.cvp.assistant.application.chat;

import org.cvp.assistant.application.port.AgentPort;
import org.cvp.assistant.application.port.ConversationRepository;
import org.cvp.assistant.application.port.VehicleAccessPort;
import org.cvp.assistant.domain.conversation.Conversation;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatServiceTests {
    @Test
    void createsConversationForAuthorizedVehicle() {
        ChatService service = service((principal, vehicle) -> true);
        ChatResult result = service.chat(new ChatCommand("user", "TEST-VEHICLE", "Why maintenance?", null, "trace"));
        assertFalse(result.conversationId().isBlank());
    }

    @Test
    void failsClosedWhenVehicleAccessIsDenied() {
        ChatService service = service((principal, vehicle) -> false);
        assertThrows(ForbiddenException.class, () -> service.chat(new ChatCommand("user", "TEST-VEHICLE", "Why maintenance?", null, "trace")));
    }

    @Test
    void rejectsConversationOwnedByAnotherPrincipal() {
        MemoryRepository repository = new MemoryRepository();
        repository.save(new Conversation("conversation", "owner", "TEST-VEHICLE", "", 0, java.time.Instant.now()));
        ChatService service = new ChatService(repository, (principal, vehicle) -> true, agent());
        assertThrows(NotFoundException.class, () -> service.chat(new ChatCommand("other", "TEST-VEHICLE", "Why maintenance?", "conversation", "trace")));
    }

    private ChatService service(VehicleAccessPort access) {
        return new ChatService(new MemoryRepository(), access, agent());
    }

    private AgentPort agent() {
        return (command, summary) -> new ChatResult(command.conversationId(), "answer", java.util.List.of(), java.util.List.of(
                ToolOutcome.failure("getMaintenanceStatus", "UPSTREAM_UNAVAILABLE", "Maintenance service unavailable", true)));
    }

    private static class MemoryRepository implements ConversationRepository {
        private final ConcurrentHashMap<String, Conversation> values = new ConcurrentHashMap<>();

        public Optional<Conversation> findById(String id) {
            return Optional.ofNullable(values.get(id));
        }

        public Conversation save(Conversation value) {
            values.put(value.id(), value);
            return value;
        }
    }
}
