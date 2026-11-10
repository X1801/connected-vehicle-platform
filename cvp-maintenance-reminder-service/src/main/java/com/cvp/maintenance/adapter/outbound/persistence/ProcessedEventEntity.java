package com.cvp.maintenance.adapter.outbound.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.*;
import java.sql.Types;

@Entity
@Table(name = "processed_event")
public class ProcessedEventEntity {
    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "sourceSystem", column = @Column(name = "source_system", length = 64, nullable = false)),
            @AttributeOverride(name = "eventId", column = @Column(name = "event_id", nullable = false)),
            @AttributeOverride(name = "consumerName", column = @Column(name = "consumer_name", length = 128, nullable = false))
    })
    public ProcessedEventId id;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "payload_checksum", nullable = false, length = 64, columnDefinition = "char(64)")
    public String payloadChecksum;
    @Column(name = "processed_at", nullable = false)
    public Instant processedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedEventEntity(ProcessedEventId id, String checksum) {
        this.id = id;
        payloadChecksum = checksum;
        processedAt = Instant.now();
    }
}
