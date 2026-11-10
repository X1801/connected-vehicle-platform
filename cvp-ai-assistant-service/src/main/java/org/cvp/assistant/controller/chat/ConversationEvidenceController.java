package org.cvp.assistant.controller.chat;

import org.cvp.assistant.application.chat.TurnTraceService;
import org.cvp.assistant.controller.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Read-only, principal-scoped inspection of a single persisted chat turn. */
@RestController
public class ConversationEvidenceController {
    private final TurnTraceService traces;
    private final boolean mockAuthenticationEnabled;

    public ConversationEvidenceController(TurnTraceService traces,
                                          @Value("${assistant.security.mock-authentication-enabled:true}") boolean mockAuthenticationEnabled) {
        this.traces = traces;
        this.mockAuthenticationEnabled = mockAuthenticationEnabled;
    }

    @GetMapping("/api/v1/assistant/conversations/{conversationId}/turns/{turnId}/evidence")
    public ResponseEntity<Map<String, Object>> evidence(@PathVariable String conversationId,
                                                          @PathVariable UUID turnId,
                                                          @RequestHeader(value = "X-Principal", required = false) String principal) {
        if (!mockAuthenticationEnabled || principal == null || principal.isBlank()) {
            throw new UnauthorizedException("Authenticated principal is required");
        }
        return ResponseEntity.ok(traces.findAuthorized(conversationId, turnId, principal));
    }
}
