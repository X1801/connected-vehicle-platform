package com.cvp.maintenance.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStatusEventTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void writesCanonicalProviderRequestId() throws Exception {
        UUID notificationId = UUID.randomUUID();
        NotificationStatusEvent event = new NotificationStatusEvent(
                notificationId, "DELIVERED", "provider-request-1", Instant.parse("2026-01-02T00:00:00Z"));

        String serialized = objectMapper.writeValueAsString(event);
        NotificationStatusEvent payload = objectMapper.readValue(
                """
                {"notificationId":"%s","status":"DELIVERED","notificationProviderRequestId":"provider-request-1","occurredAt":"2026-01-02T00:00:00Z"}
                """.formatted(notificationId), NotificationStatusEvent.class);

        assertThat(serialized).contains("\"notificationProviderRequestId\":\"provider-request-1\"");
        assertThat(objectMapper.readTree(serialized).size()).isEqualTo(4);
        assertThat(payload.notificationProviderRequestId()).isEqualTo("provider-request-1");
    }
}
