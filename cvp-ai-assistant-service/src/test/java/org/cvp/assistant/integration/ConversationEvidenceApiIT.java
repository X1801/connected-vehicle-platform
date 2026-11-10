package org.cvp.assistant.integration;

import org.cvp.assistant.application.chat.ChatCommand;
import org.cvp.assistant.application.chat.ChatResult;
import org.cvp.assistant.application.chat.TurnTraceService;
import org.cvp.assistant.domain.conversation.Conversation;
import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"assistant.rag.ingest-on-startup=false", "assistant.deepseek.enabled=false"})
@ActiveProfiles("persistent")
@Testcontainers
class ConversationEvidenceApiIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TurnTraceService traces;
    @LocalServerPort int port;

    @Test
    void returnsOnlyTheOwnersPersistedEvidenceAndHidesOtherConversations() throws Exception {
        String conversationId = "api-evidence";
        EvidenceSource source = new EvidenceSource("maintenance-reminder-api-evidence", "BUSINESS_API", "MAINTENANCE_REMINDER", "TEST-VEHICLE", Instant.now(), null, "DIRECT", "E1");
        ChatResult result = new ChatResult(conversationId, "Verified pending [E1]", List.of(source),
                List.of(ToolOutcome.success("getMaintenanceStatus", "ok", "status=PENDING", List.of(source))), List.of(), List.of("E1"), false, null);
        var turnId = traces.save(new Conversation(conversationId, "principal-a", "TEST-VEHICLE", "summary", 0, Instant.now()),
                new ChatCommand("principal-a", "TEST-VEHICLE", "question", conversationId, "api-correlation"), result);

        String path = "/api/v1/assistant/conversations/" + conversationId + "/turns/" + turnId + "/evidence";
        HttpResponse<String> owner = get(path, "principal-a");
        assertEquals(200, owner.statusCode());
        assertTrue(owner.body().contains("\"conversationId\":\"" + conversationId + "\""));
        assertTrue(owner.body().contains("\"E1\""));
        assertTrue(owner.body().contains("TEST-VEHICLE"));
        assertEquals(404, get(path, "principal-b").statusCode());
        assertEquals(401, get(path, null).statusCode());
        assertEquals(404, get("/api/v1/assistant/conversations/not-the-owner/turns/" + turnId + "/evidence", "principal-a").statusCode());
    }

    private HttpResponse<String> get(String path, String principal) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (principal != null) request.header("X-Principal", principal);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
