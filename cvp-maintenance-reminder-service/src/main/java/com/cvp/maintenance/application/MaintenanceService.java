package com.cvp.maintenance.application;

import com.cvp.maintenance.adapter.inbound.kafka.TelemetryMaintenanceStateEvent;
import com.cvp.maintenance.adapter.outbound.persistence.*;
import com.cvp.maintenance.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class MaintenanceService {
    private static final String TELEMETRY = "Telemetry", CONSUMER = "maintenance-reminder-state";
    private final ProcessedEventRepository processed;
    private final VehicleMaintenanceStatusRepository statuses;
    private final MaintenanceIndicatorRepository indicators;
    private final VehicleReferenceRepository references;
    private final MaintenancePlanRepository plans;
    private final ReminderRepository reminders;
    private final ReminderNotificationRepository notifications;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public MaintenanceService(ProcessedEventRepository processed, VehicleMaintenanceStatusRepository statuses, MaintenanceIndicatorRepository indicators, VehicleReferenceRepository references, MaintenancePlanRepository plans, ReminderRepository reminders, ReminderNotificationRepository notifications, OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.processed = processed;
        this.statuses = statuses;
        this.indicators = indicators;
        this.references = references;
        this.plans = plans;
        this.reminders = reminders;
        this.notifications = notifications;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ingest(TelemetryMaintenanceStateEvent event) {
        validate(event);
        String checksum = checksum(canonical(event));
        ProcessedEventId eventId = new ProcessedEventId(TELEMETRY, event.eventId(), CONSUMER);
        if (processed.existsById(eventId)) return;
        VehicleMaintenanceStatusEntity status = statuses.findById(event.vehicleId()).orElse(null);
        SourceOrdering.Decision decision = status == null ? SourceOrdering.Decision.ACCEPT : SourceOrdering.decide(event.sourceVersion(), event.sourceTimestamp(), checksum, status.sourceVersion, status.sourceTimestamp, status.payloadChecksum);
        processed.save(new ProcessedEventEntity(eventId, checksum));
        if (decision == SourceOrdering.Decision.STALE || decision == SourceOrdering.Decision.IDENTICAL) return;
        if (decision == SourceOrdering.Decision.CONFLICT)
            throw new ConflictException("Equal Telemetry source version has conflicting payload");
        if (status == null) status = new VehicleMaintenanceStatusEntity(event.vehicleId());
        status.odometerKm = event.odometerKm();
        status.sourceTimestamp = event.sourceTimestamp();
        status.sourceVersion = event.sourceVersion();
        status.payloadChecksum = checksum;
        status.acceptedAt = Instant.now();
        statuses.save(status);
        indicators.deleteByStatusVehicleId(event.vehicleId());
        // A full Telemetry snapshot replaces the current indicator set. Flush the deletes before
        // inserting replacement rows so an unchanged indicator code never violates its
        // unique (vehicle_id, indicator_code) constraint in the same transaction.
        indicators.flush();
        for (TelemetryMaintenanceStateEvent.Indicator source : event.serviceIndicators()) {
            MaintenanceIndicatorEntity indicator = new MaintenanceIndicatorEntity();
            indicator.id = UUID.randomUUID();
            indicator.status = status;
            indicator.indicatorCode = source.code();
            indicator.dueOdometerKm = source.dueOdometerKm();
            indicator.dueDate = source.dueDate();
            indicator.remainingDistanceKm = source.remainingDistanceKm();
            indicator.remainingDays = source.remainingDays();
            indicator.severity = ReminderSeverity.valueOf(source.severity());
            indicator.sourceTimestamp = source.sourceTimestamp() == null ? event.sourceTimestamp() : source.sourceTimestamp();
            indicator.maintenanceCycleId = source.maintenanceCycleId();
            indicators.save(indicator);
        }
        evaluate(event.vehicleId(), status, event.serviceIndicators());
    }

    @Transactional
    public void reevaluate(String vehicleId) {
        VehicleMaintenanceStatusEntity status = statuses.findById(vehicleId).orElseThrow(() -> new NotFoundException("Maintenance status not found"));
        List<TelemetryMaintenanceStateEvent.Indicator> current = indicators.findByStatusVehicleId(vehicleId).stream().map(i -> new TelemetryMaintenanceStateEvent.Indicator(i.indicatorCode, i.dueOdometerKm, i.dueDate, i.remainingDistanceKm, i.remainingDays, i.severity.name(), i.sourceTimestamp, i.maintenanceCycleId)).toList();
        evaluate(vehicleId, status, current);
    }

    private void evaluate(String vehicleId, VehicleMaintenanceStatusEntity status, List<TelemetryMaintenanceStateEvent.Indicator> sourceIndicators) {
        VehicleReferenceEntity ref = references.findById(vehicleId).orElse(null);
        if (ref == null) return;
        for (TelemetryMaintenanceStateEvent.Indicator indicator : sourceIndicators) {
            MaintenancePlanEntity plan = plans.findActive(indicator.code(), LocalDate.now()).stream().filter(p -> matches(p, ref)).findFirst().orElse(null);
            if (plan == null) continue;
            Long dueKm = indicator.dueOdometerKm() != null ? indicator.dueOdometerKm() : (plan.distanceThresholdKm == null ? null : status.odometerKm + plan.distanceThresholdKm);
            LocalDate dueDate = indicator.dueDate() != null ? indicator.dueDate() : (plan.timeThresholdDays == null ? null : LocalDate.now().plusDays(plan.timeThresholdDays));
            if (dueKm == null && dueDate == null) continue;
            String legacyCycle = MaintenanceCycleReference.calculate(vehicleId, indicator.code(), plan.policyVersion, plan.applicabilityScopeIdentity, dueKm, dueDate);
            String cycle = indicator.maintenanceCycleId() == null ? legacyCycle
                    : MaintenanceCycleReference.calculateAuthoritative(vehicleId, indicator.code(), indicator.maintenanceCycleId());
            ReminderEntity existing = findExistingCycle(vehicleId, indicator.code(), indicator.maintenanceCycleId(), legacyCycle);
            if (existing != null)
                continue;
            ReminderEntity reminder = new ReminderEntity();
            reminder.id = UUID.randomUUID();
            reminder.vehicleId = vehicleId;
            reminder.maintenanceType = indicator.code();
            reminder.maintenanceCycleReference = cycle;
            reminder.authoritativeCycleId = indicator.maintenanceCycleId();
            reminder.dueOdometerKm = dueKm;
            reminder.dueDate = dueDate;
            reminder.remainingDistanceKm = indicator.remainingDistanceKm();
            reminder.remainingDays = indicator.remainingDays();
            reminder.severity = plan.severityPolicy;
            reminder.policyVersion = plan.policyVersion;
            reminder.status = ReminderStatus.PENDING;
            reminder.nextActionAt = Instant.now();
            reminder.deduplicationKey = vehicleId + ":" + indicator.code() + ":" + cycle;
            reminder.createdAt = Instant.now();
            reminder.updatedAt = reminder.createdAt;
            reminders.save(reminder);
            scheduleNotification(reminder);
        }
    }

    /**
     * Legacy reminders predate the Telemetry-owned identity. They are only mapped when the
     * old deterministic reference matches and the row has no authoritative identity.
     * A row already bound to C1 is immutable with respect to a later C2, even when
     * their historical due data produces the same legacy reference.
     */
    private ReminderEntity findExistingCycle(String vehicleId, String maintenanceType, UUID authoritativeCycleId, String legacyCycle) {
        if (authoritativeCycleId != null) {
            Optional<ReminderEntity> stable = reminders.findByVehicleIdAndMaintenanceTypeAndAuthoritativeCycleId(vehicleId, maintenanceType, authoritativeCycleId);
            if (stable.isPresent()) return stable.get();
            Optional<ReminderEntity> legacy = reminders.findByVehicleIdAndMaintenanceTypeAndMaintenanceCycleReference(vehicleId, maintenanceType, legacyCycle);
            if (legacy.isPresent() && legacy.get().authoritativeCycleId == null) {
                legacy.get().authoritativeCycleId = authoritativeCycleId;
                return reminders.save(legacy.get());
            }
            return null;
        }
        return reminders.findByVehicleIdAndMaintenanceTypeAndMaintenanceCycleReference(vehicleId, maintenanceType, legacyCycle).orElse(null);
    }

    private boolean matches(MaintenancePlanEntity p, VehicleReferenceEntity r) {
        return (p.modelCode == null || p.modelCode.equals(r.modelCode)) && (p.marketCode == null || p.marketCode.equals(r.marketCode)) && (p.powertrainCode == null || p.powertrainCode.equals(r.powertrainCode)) && (p.modelYearFrom == null || r.modelYear != null && r.modelYear >= p.modelYearFrom) && (p.modelYearTo == null || r.modelYear != null && r.modelYear <= p.modelYearTo);
    }

    private void scheduleNotification(ReminderEntity reminder) {
        reminder.status = ReminderStatus.SCHEDULED;
        reminder.updatedAt = Instant.now();
        reminders.save(reminder);
        String key = reminder.id + ":INITIAL:" + reminder.nextActionAt;
        ReminderNotificationEntity n = new ReminderNotificationEntity();
        n.id = UUID.randomUUID();
        n.reminder = reminder;
        n.channel = "IN_APP";
        n.status = NotificationStatus.PENDING;
        n.idempotencyKey = key;
        n.attemptCount = 0;
        n.createdAt = Instant.now();
        n.updatedAt = n.createdAt;
        notifications.save(n);
        OutboxEventEntity e = new OutboxEventEntity();
        e.id = UUID.randomUUID();
        e.aggregateType = "NOTIFICATION";
        e.aggregateId = n.id;
        e.eventType = "maintenance-reminder.notification.requested";
        e.schemaVersion = 1;
        e.payload = json(Map.of("eventId", e.id, "notificationId", n.id, "reminderId", reminder.id, "idempotencyKey", key, "vehicleId", reminder.vehicleId));
        e.publicationStatus = OutboxStatus.PENDING;
        e.attemptCount = 0;
        e.nextAttemptAt = Instant.now();
        e.createdAt = Instant.now();
        outbox.save(e);
    }

    public ReminderEntity reminder(UUID id) {
        return reminders.findById(id).orElseThrow(() -> new NotFoundException("Reminder not found"));
    }

    public VehicleMaintenanceStatusEntity status(String id) {
        return statuses.findById(id).orElseThrow(() -> new NotFoundException("Maintenance status not found"));
    }

    @Transactional
    public ReminderEntity acknowledge(UUID id) {
        ReminderEntity r = reminder(id);
        if (r.status == ReminderStatus.DISMISSED || r.status == ReminderStatus.EXPIRED)
            throw new ConflictException("Reminder is terminal");
        r.status = ReminderStatus.ACKNOWLEDGED;
        r.updatedAt = Instant.now();
        return reminders.save(r);
    }

    @Transactional
    public ReminderEntity dismiss(UUID id) {
        ReminderEntity r = reminder(id);
        if (r.severity == ReminderSeverity.CRITICAL)
            throw new ConflictException("Critical reminder cannot be dismissed");
        if (r.status == ReminderStatus.DISMISSED || r.status == ReminderStatus.EXPIRED)
            throw new ConflictException("Reminder is terminal");
        r.status = ReminderStatus.DISMISSED;
        r.updatedAt = Instant.now();
        return reminders.save(r);
    }

    private void validate(TelemetryMaintenanceStateEvent e) {
        if (e.eventId() == null || e.vehicleId() == null || e.vehicleId().isBlank() || e.sourceTimestamp() == null || e.odometerKm() < 0)
            throw new IllegalArgumentException("Invalid Telemetry maintenance event");
    }

    private String canonical(TelemetryMaintenanceStateEvent e) {
        try {
            return objectMapper.writeValueAsString(e);
        } catch (JsonProcessingException x) {
            throw new IllegalArgumentException("Cannot serialize event", x);
        }
    }

    private String json(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException x) {
            throw new IllegalStateException(x);
        }
    }

    private String checksum(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (NoSuchAlgorithmException x) {
            throw new IllegalStateException(x);
        }
    }
}
