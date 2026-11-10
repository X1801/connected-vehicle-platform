package com.cvp.maintenance.adapter.outbound.kafka;

import com.cvp.maintenance.adapter.outbound.persistence.*;
import com.cvp.maintenance.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository events;
    private final ReminderNotificationRepository notifications;
    private final KafkaTemplate<String, String> kafka;
    @Value("${maintenance-reminder.integration.notification.notification-request-topic}")
    String topic;

    public OutboxPublisher(OutboxEventRepository e, ReminderNotificationRepository n, KafkaTemplate<String, String> k) {
        events = e;
        notifications = n;
        kafka = k;
    }

    @Scheduled(fixedDelayString = "${maintenance-reminder.outbox.fixed-delay-ms:1000}")
    public void publish() {
        for (OutboxEventEntity e : events.findEligible(Instant.now(), PageRequest.of(0, 50))) publishOne(e.id);
    }

    @Transactional
    public void publishOne(UUID id) {
        OutboxEventEntity e = events.findById(id).orElse(null);
        if (e == null || !(e.publicationStatus == OutboxStatus.PENDING || e.publicationStatus == OutboxStatus.FAILED) || e.nextAttemptAt.isAfter(Instant.now()))
            return;
        e.publicationStatus = OutboxStatus.IN_PROGRESS;
        e.claimToken = UUID.randomUUID();
        e.claimedAt = Instant.now();
        e.claimExpiresAt = e.claimedAt.plusSeconds(30);
        events.saveAndFlush(e);
        try {
            kafka.send(topic, e.aggregateId.toString(), e.payload).get();
            e.publicationStatus = OutboxStatus.PUBLISHED;
            e.publishedAt = Instant.now();
            notifications.findById(e.aggregateId).ifPresent(n -> {
                n.status = NotificationStatus.PUBLISHED;
                n.publishedAt = e.publishedAt;
                n.updatedAt = e.publishedAt;
                notifications.save(n);
            });
        } catch (Exception x) {
            e.attemptCount++;
            e.publicationStatus = e.attemptCount >= 5 ? OutboxStatus.FAILED : OutboxStatus.PENDING;
            e.nextAttemptAt = Instant.now().plusSeconds(Math.min(60, 1L << Math.min(e.attemptCount, 6)));
            e.lastFailureCode = x.getClass().getSimpleName();
        } finally {
            e.claimToken = null;
            e.claimedAt = null;
            e.claimExpiresAt = null;
            events.save(e);
        }
    }
}
