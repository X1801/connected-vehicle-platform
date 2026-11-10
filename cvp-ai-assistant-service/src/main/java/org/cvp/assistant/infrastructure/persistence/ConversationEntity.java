package org.cvp.assistant.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "conversation", indexes = @Index(name = "conversation_principal_vehicle_idx", columnList = "principal,vehicle_id"))
public class ConversationEntity {
    @Id
    @Column(length = 80)
    String id;
    @Column(nullable = false)
    String principal;
    @Column(name = "vehicle_id", nullable = false, length = 80)
    String vehicleId;
    @Column(nullable = false, columnDefinition = "text")
    String summary;
    @Version
    long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected ConversationEntity() {
    }

    public ConversationEntity(String id, String principal, String vehicleId, String summary, long version, Instant updatedAt) {
        this.id = id;
        this.principal = principal;
        this.vehicleId = vehicleId;
        this.summary = summary;
        this.version = version;
        this.createdAt = updatedAt;
        this.updatedAt = updatedAt;
    }

    public String id() {
        return id;
    }

    public String principal() {
        return principal;
    }

    public String vehicleId() {
        return vehicleId;
    }

    public String summary() {
        return summary;
    }

    public long version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void update(String summary, Instant updatedAt) {
        this.summary = summary;
        this.updatedAt = updatedAt;
    }
}
