package org.cvp.assistant.controller.chat;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.cvp.assistant.application.chat.ChatCommand;
import org.cvp.assistant.application.chat.ChatResult;
import org.cvp.assistant.application.chat.ChatUseCase;
import org.cvp.assistant.controller.UnauthorizedException;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ChatController {
    private final ChatUseCase chat;
    private final MeterRegistry meters;
    private final boolean mockAuthenticationEnabled;
    public ChatController(ChatUseCase chat, MeterRegistry meters,
                          @Value("${assistant.security.mock-authentication-enabled:true}") boolean mockAuthenticationEnabled) {
        this.chat = chat;
        this.meters = meters;
        this.mockAuthenticationEnabled = mockAuthenticationEnabled;
    }

    @PostMapping("/api/v1/assistant/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                              @RequestHeader(value = "X-Principal", required = false) String principal,
                                              @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        if (!mockAuthenticationEnabled || principal == null || principal.isBlank()) {
            throw new UnauthorizedException("Authenticated principal is required");
        }
        long started = System.nanoTime();
        ChatResult result = chat.chat(new ChatCommand(principal, request.vehicleId(), request.userMessage(), request.conversationId(),
                correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId));
        meters.timer("assistant.chat.duration", "outcome", "success").record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        return ResponseEntity.ok(new ChatResponse(result.conversationId(), result.answer(), result.sources(), result.toolResults(), result.turnId()));
    }
}
