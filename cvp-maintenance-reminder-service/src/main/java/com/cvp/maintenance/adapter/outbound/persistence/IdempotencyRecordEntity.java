package com.cvp.maintenance.adapter.outbound.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.*;
import java.sql.Types;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecordEntity {
    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "callerId", column = @Column(name = "caller_id", length = 128, nullable = false)),
            @AttributeOverride(name = "idempotencyKey", column = @Column(name = "idempotency_key", length = 256, nullable = false))
    })
    public IdempotencyRecordId id;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    public String requestHash;
    @Column(name = "response_status", nullable = false, columnDefinition = "smallint")
    public short responseStatus;
    @Column(name = "result_resource_type", length = 64) public String resultResourceType;
    @Column(name = "result_resource_id") public UUID resultResourceId;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    public IdempotencyRecordEntity() {
    }
}
