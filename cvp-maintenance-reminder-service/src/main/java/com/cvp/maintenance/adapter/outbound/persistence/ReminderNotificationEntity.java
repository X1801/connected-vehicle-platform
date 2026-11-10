package com.cvp.maintenance.adapter.outbound.persistence;

import com.cvp.maintenance.domain.NotificationStatus;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "reminder_notification")
public class ReminderNotificationEntity {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reminder_id", nullable = false) public ReminderEntity reminder;
    public String recipientReference; @Column(nullable = false) public String channel;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public NotificationStatus status;
    @Column(nullable = false) public String idempotencyKey; @Column(name = "notification_provider_request_id", unique = true) public String notificationProviderRequestId;
    @Column(nullable = false) public int attemptCount; @Column(nullable = false) public Instant createdAt; @Column(nullable = false) public Instant updatedAt;
    public Instant publishedAt; public Instant acceptedAt; public Instant deliveredAt; public Instant failedAt; public String lastFailureCode;
    public ReminderNotificationEntity() { }
}
