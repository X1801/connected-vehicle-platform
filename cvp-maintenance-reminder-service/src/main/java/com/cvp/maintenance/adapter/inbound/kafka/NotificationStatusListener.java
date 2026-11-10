package com.cvp.maintenance.adapter.inbound.kafka;

import com.cvp.maintenance.adapter.outbound.persistence.ReminderNotificationEntity;
import com.cvp.maintenance.adapter.outbound.persistence.ReminderNotificationRepository;
import com.cvp.maintenance.domain.NotificationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class NotificationStatusListener {
    private final ReminderNotificationRepository notifications;
    private final ObjectMapper objectMapper;

    public NotificationStatusListener(ReminderNotificationRepository notifications, ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${maintenance-reminder.integration.notification.notification-status-topic}")
    @Transactional
    public void onMessage(String payload) throws Exception {
        NotificationStatusEvent event = objectMapper.readValue(payload, NotificationStatusEvent.class);
        ReminderNotificationEntity notification = notifications.findById(event.notificationId()).orElse(null);
        if (notification == null) {
            return;
        }
        NotificationStatus status = NotificationStatus.valueOf(event.status());
        Instant occurredAt = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        notification.status = status;
        notification.notificationProviderRequestId = event.notificationProviderRequestId();
        notification.updatedAt = occurredAt;
        switch (status) {
            case ACCEPTED -> notification.acceptedAt = occurredAt;
            case DELIVERED -> {
                notification.acceptedAt = notification.acceptedAt == null ? occurredAt : notification.acceptedAt;
                notification.deliveredAt = occurredAt;
            }
            case FAILED -> notification.failedAt = occurredAt;
            case PUBLISHED -> notification.publishedAt = occurredAt;
            case PENDING -> { }
        }
        notifications.save(notification);
    }
}
