package com.cvp.maintenance.adapter.inbound.kafka;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Proposed local/test contract; the production notification-provider schema remains an open integration decision.
 */
public record NotificationStatusEvent(UUID notificationId, String status, @JsonProperty("notificationProviderRequestId") String notificationProviderRequestId, Instant occurredAt) {
}
