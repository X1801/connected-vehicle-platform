package com.cvp.maintenance.adapter.outbound.persistence;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProcessedEventId implements Serializable {
    public String sourceSystem;
    public UUID eventId;
    public String consumerName;

    protected ProcessedEventId() {
    }

    public ProcessedEventId(String sourceSystem, UUID eventId, String consumerName) {
        this.sourceSystem = sourceSystem;
        this.eventId = eventId;
        this.consumerName = consumerName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProcessedEventId that)) {
            return false;
        }
        return Objects.equals(sourceSystem, that.sourceSystem)
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(consumerName, that.consumerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceSystem, eventId, consumerName);
    }
}
