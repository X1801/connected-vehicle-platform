package com.cvp.maintenance;

import com.cvp.maintenance.adapter.inbound.kafka.NotificationStatusEvent;
import com.cvp.maintenance.adapter.inbound.kafka.NotificationStatusListener;
import com.cvp.maintenance.adapter.inbound.kafka.TelemetryMaintenanceStateEvent;
import com.cvp.maintenance.adapter.outbound.kafka.OutboxPublisher;
import com.cvp.maintenance.adapter.outbound.persistence.*;
import com.cvp.maintenance.application.ConflictException;
import com.cvp.maintenance.application.MaintenanceService;
import com.cvp.maintenance.application.ReminderQueryService;
import com.cvp.maintenance.domain.NotificationStatus;
import com.cvp.maintenance.domain.ReminderSeverity;
import com.cvp.maintenance.domain.ReminderStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Real PostgreSQL/Flyway/JPA and Kafka verification for the Phase 1 path.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class Phase1PostgresKafkaIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("maintenance_reminder_test").withUsername("test").withPassword("test");
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("maintenance-reminder.outbox.fixed-delay-ms", () -> "600000");
    }

    @Autowired
    MaintenanceService maintenance;
    @Autowired
    OutboxPublisher publisher;
    @Autowired
    ReminderRepository reminders;
    @Autowired
    ReminderNotificationRepository notifications;
    @Autowired
    OutboxEventRepository outbox;
    @Autowired
    VehicleMaintenanceStatusRepository statuses;
    @Autowired
    ProcessedEventRepository processed;
    @Autowired
    NotificationStatusListener notificationStatusListener;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ReminderQueryService reminderQueries;

    @BeforeEach
    void reset() {
        jdbc.execute("delete from outbox_event");
        jdbc.execute("delete from reminder_notification");
        jdbc.execute("delete from reminder");
        jdbc.execute("delete from maintenance_indicator");
        jdbc.execute("delete from vehicle_maintenance_status");
        jdbc.execute("delete from processed_event");
        jdbc.execute("delete from idempotency_record");
        jdbc.execute("delete from maintenance_plan");
        jdbc.execute("delete from vehicle_reference");
    }

    @Test
    void happyDuplicateStaleConflictAndNotificationStatusUseRealPostgresAndKafka() throws Exception {
        seedApplicableVehicleAndPlan("VIN-IT");
        TelemetryMaintenanceStateEvent accepted = event(UUID.randomUUID(), "VIN-IT", 10, 20_000, "WARNING");
        maintenance.ingest(accepted);
        assertThat(statuses.findById("VIN-IT")).isPresent();
        assertThat(reminders.count()).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(1);
        OutboxEventEntity event = outbox.findAll().getFirst();

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of("cvp.notification-request.v1"));
            consumer.poll(Duration.ofSeconds(5)); // establish assignment before publication
            publisher.publishOne(event.id);
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(15));
            assertThat(records.records("cvp.notification-request.v1"))
                    .extracting(record -> record.value())
                    .contains(event.payload);
        }
        assertThat(outbox.findById(event.id).orElseThrow().publicationStatus.name()).isEqualTo("PUBLISHED");
        ReminderNotificationEntity notification = notifications.findAll().getFirst();
        assertThat(notification.status).isEqualTo(NotificationStatus.PUBLISHED);

        maintenance.ingest(accepted);
        assertThat(processed.count()).isEqualTo(1);
        assertThat(reminders.count()).isEqualTo(1);
        maintenance.ingest(event(UUID.randomUUID(), "VIN-IT", 9, 1, "WARNING"));
        assertThat(statuses.findById("VIN-IT").orElseThrow().odometerKm).isEqualTo(20_000);
        assertThatThrownBy(() -> maintenance.ingest(event(UUID.randomUUID(), "VIN-IT", 10, 20_001, "WARNING")))
                .isInstanceOf(ConflictException.class);

        notificationStatusListener.onMessage("{\"notificationId\":\"" + notification.id + "\",\"status\":\"DELIVERED\",\"notificationProviderRequestId\":\"notification-provider-it-1\",\"occurredAt\":\"2026-01-02T00:00:00Z\"}");
        assertThat(notifications.findById(notification.id).orElseThrow().status).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(reminders.findAll().getFirst().status.name()).isEqualTo("SCHEDULED");
    }

    @Test
    void missingVehicleReferenceDefersEvaluation() {
        jdbc.update("insert into maintenance_plan(id, maintenance_type, applicability_scope_identity, distance_threshold_km, severity_policy, policy_version, valid_from, created_at) values (?,?,?,?,?,?,?,current_timestamp)", UUID.randomUUID(), "OIL", "default", 1000L, "WARNING", "v1", java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
        maintenance.ingest(event(UUID.randomUUID(), "VIN-NO-REF", 1, 100, "WARNING"));
        assertThat(statuses.findById("VIN-NO-REF")).isPresent();
        assertThat(reminders.count()).isZero();
    }

    @Test
    void newerFullSnapshotReplacesExistingIndicatorsWithoutUniqueConstraintConflict() {
        Instant firstTimestamp = Instant.parse("2026-01-03T00:00:00Z");
        maintenance.ingest(new TelemetryMaintenanceStateEvent(UUID.randomUUID(), "maintenance-state", "VIN-SNAPSHOT", firstTimestamp, 1L, firstTimestamp, 10_000L,
                List.of(new TelemetryMaintenanceStateEvent.Indicator("OIL", 15_000L, null, 5_000L, null, "WARNING", firstTimestamp))));

        Instant secondTimestamp = firstTimestamp.plusSeconds(1);
        maintenance.ingest(new TelemetryMaintenanceStateEvent(UUID.randomUUID(), "maintenance-state", "VIN-SNAPSHOT", secondTimestamp, 2L, secondTimestamp, 10_100L,
                List.of(
                        new TelemetryMaintenanceStateEvent.Indicator("OIL", 15_000L, null, 4_900L, null, "WARNING", secondTimestamp),
                        new TelemetryMaintenanceStateEvent.Indicator("BRAKE", 20_000L, null, 9_900L, null, "INFO", secondTimestamp))));

        assertThat(statuses.findById("VIN-SNAPSHOT").orElseThrow().sourceVersion).isEqualTo(2);
        assertThat(jdbc.queryForList("select indicator_code from maintenance_indicator where vehicle_id = ? order by indicator_code", "VIN-SNAPSHOT"))
                .extracting(row -> row.get("indicator_code")).containsExactly("BRAKE", "OIL");
    }

    @Test
    void reminderQueryFiltersPaginatesDeterministicallyAndUsesLatestNotificationStatus() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ReminderEntity warningScheduled = reminder("QUERY-VEHICLE", ReminderStatus.SCHEDULED, ReminderSeverity.WARNING, base.plusSeconds(30));
        ReminderEntity warningAcknowledged = reminder("QUERY-VEHICLE", ReminderStatus.ACKNOWLEDGED, ReminderSeverity.WARNING, base.plusSeconds(20));
        ReminderEntity criticalScheduled = reminder("QUERY-VEHICLE", ReminderStatus.SCHEDULED, ReminderSeverity.CRITICAL, base.plusSeconds(10));
        ReminderEntity noNotification = reminder("QUERY-VEHICLE", ReminderStatus.PENDING, ReminderSeverity.INFO, base);
        reminders.saveAll(List.of(warningScheduled, warningAcknowledged, criticalScheduled, noNotification));
        notifications.save(notification(warningScheduled, NotificationStatus.PUBLISHED, base.plusSeconds(5)));
        notifications.save(notification(warningScheduled, NotificationStatus.DELIVERED, base.plusSeconds(15)));
        notifications.flush();

        assertThat(reminderQueries.list("QUERY-VEHICLE", null, null, null, null))
                .extracting(result -> result.reminder().id)
                .containsExactly(warningScheduled.id, warningAcknowledged.id, criticalScheduled.id, noNotification.id);
        assertThat(reminderQueries.list("QUERY-VEHICLE", ReminderStatus.SCHEDULED, null, null, null)).hasSize(2);
        assertThat(reminderQueries.list("QUERY-VEHICLE", null, ReminderSeverity.WARNING, null, null)).hasSize(2);
        assertThat(reminderQueries.list("QUERY-VEHICLE", ReminderStatus.SCHEDULED, ReminderSeverity.WARNING, null, null)).hasSize(1);
        assertThat(reminderQueries.list("QUERY-VEHICLE", null, null, 0, 2)).extracting(result -> result.reminder().id)
                .containsExactly(warningScheduled.id, warningAcknowledged.id);
        assertThat(reminderQueries.list("QUERY-VEHICLE", null, null, 1, 2)).extracting(result -> result.reminder().id)
                .containsExactly(criticalScheduled.id, noNotification.id);
        assertThat(reminderQueries.get(warningScheduled.id).notificationStatus()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(reminderQueries.get(noNotification.id).notificationStatus()).isNull();
        assertThat(reminders.findById(warningScheduled.id).orElseThrow().status).isEqualTo(ReminderStatus.SCHEDULED);
        assertThatIllegalArgumentException().isThrownBy(() -> reminderQueries.list("QUERY-VEHICLE", null, null, -1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> reminderQueries.list("QUERY-VEHICLE", null, null, 0, 101));
    }

    private ReminderEntity reminder(String vehicleId, ReminderStatus status, ReminderSeverity severity, Instant updatedAt) {
        ReminderEntity reminder = new ReminderEntity();
        reminder.id = UUID.randomUUID();
        reminder.vehicleId = vehicleId;
        reminder.maintenanceType = "OIL";
        reminder.maintenanceCycleReference = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        reminder.dueOdometerKm = 10_000L;
        reminder.severity = severity;
        reminder.policyVersion = "v1";
        reminder.status = status;
        reminder.deduplicationKey = reminder.id.toString();
        reminder.createdAt = updatedAt.minusSeconds(1);
        reminder.updatedAt = updatedAt;
        return reminder;
    }

    private ReminderNotificationEntity notification(ReminderEntity reminder, NotificationStatus status, Instant updatedAt) {
        ReminderNotificationEntity notification = new ReminderNotificationEntity();
        notification.id = UUID.randomUUID();
        notification.reminder = reminder;
        notification.channel = "IN_APP";
        notification.status = status;
        notification.idempotencyKey = notification.id.toString();
        notification.attemptCount = 0;
        notification.createdAt = updatedAt.minusSeconds(1);
        notification.updatedAt = updatedAt;
        notification.publishedAt = updatedAt;
        if (status == NotificationStatus.DELIVERED) {
            notification.acceptedAt = updatedAt;
            notification.deliveredAt = updatedAt;
        }
        return notification;
    }

    private KafkaConsumer<String, String> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "phase1-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private void seedApplicableVehicleAndPlan(String vehicleId) {
        jdbc.update("insert into vehicle_reference(vehicle_id, model_code, model_year, market_code, powertrain_code, refreshed_at) values (?,?,?,?,?,current_timestamp)", vehicleId, "A4", 2024, "DE", "ICE");
        jdbc.update("insert into maintenance_plan(id, maintenance_type, applicability_scope_identity, model_code, distance_threshold_km, severity_policy, policy_version, valid_from, created_at) values (?,?,?,?,?,?,?,?,current_timestamp)", UUID.randomUUID(), "OIL", "a4-de", "A4", 1000L, "WARNING", "v1", java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
    }

    private TelemetryMaintenanceStateEvent event(UUID id, String vehicleId, long sourceVersion, long odometer, String severity) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(sourceVersion);
        return new TelemetryMaintenanceStateEvent(id, "maintenance-state", vehicleId, timestamp, sourceVersion, timestamp, odometer,
                List.of(new TelemetryMaintenanceStateEvent.Indicator("OIL", odometer + 1000, null, 1000L, null, severity, timestamp)));
    }
}
