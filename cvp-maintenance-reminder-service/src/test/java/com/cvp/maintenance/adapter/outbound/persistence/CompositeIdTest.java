package com.cvp.maintenance.adapter.outbound.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CompositeIdTest {

    @Test
    void idempotencyIdsUseBothKeyFields() {
        IdempotencyRecordId first = new IdempotencyRecordId("caller", "key");
        IdempotencyRecordId same = new IdempotencyRecordId("caller", "key");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, new IdempotencyRecordId("other", "key"));
    }

    @Test
    void processedEventIdsUseAllKeyFields() {
        UUID eventId = UUID.randomUUID();
        ProcessedEventId first = new ProcessedEventId("Telemetry", eventId, "consumer");
        ProcessedEventId same = new ProcessedEventId("Telemetry", eventId, "consumer");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, new ProcessedEventId("Telemetry", eventId, "other-consumer"));
    }
}
