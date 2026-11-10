package com.cvp.maintenance.application;

import com.cvp.maintenance.adapter.inbound.kafka.TelemetryMaintenanceStateEvent;
import com.cvp.maintenance.adapter.outbound.persistence.*;
import com.cvp.maintenance.domain.ReminderSeverity;
import com.cvp.maintenance.domain.ReminderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MaintenanceServiceCycleSafetyTest {
    private static final String VEHICLE = "TEST-CYCLE-SAFETY";
    private static final Instant SOURCE_TIME = Instant.parse("2026-10-09T09:44:00Z");

    @Test
    void reevaluationDoesNotReopenOrRenotifyCompletedSameCycle() {
        Fixture fixture = new Fixture();
        VehicleMaintenanceStatusEntity status = status(1L);
        when(fixture.statuses.findById(VEHICLE)).thenReturn(Optional.of(status));
        when(fixture.indicators.findByStatusVehicleId(VEHICLE)).thenReturn(List.of(indicator(status)));
        completedCycle(fixture);

        fixture.service.reevaluate(VEHICLE);

        verify(fixture.reminders, never()).save(any(ReminderEntity.class));
        verifyNoInteractions(fixture.notifications, fixture.outbox);
    }

    @Test
    void newerSameCycleSnapshotUpdatesTelemetryButDoesNotCreateSecondReminderOrNotification() {
        Fixture fixture = new Fixture();
        VehicleMaintenanceStatusEntity status = status(1L);
        when(fixture.processed.existsById(any())).thenReturn(false);
        when(fixture.statuses.findById(VEHICLE)).thenReturn(Optional.of(status));
        completedCycle(fixture);

        fixture.service.ingest(event(2L, UUID.randomUUID()));

        verify(fixture.statuses).save(status);
        verify(fixture.indicators).deleteByStatusVehicleId(VEHICLE);
        verify(fixture.indicators).flush();
        verify(fixture.reminders, never()).save(any(ReminderEntity.class));
        verifyNoInteractions(fixture.notifications, fixture.outbox);
    }

    @Test
    void authoritativeCycleMapsTheExactLegacyC1WithoutChangingItsLegacyReference() {
        Fixture fixture = new Fixture();
        VehicleMaintenanceStatusEntity status = status(1L);
        UUID cycleId = UUID.randomUUID();
        ReminderEntity legacy = completedCycle(fixture);
        legacy.maintenanceCycleReference = "legacy-c1-reference";
        when(fixture.statuses.findById(VEHICLE)).thenReturn(Optional.of(status));
        when(fixture.indicators.findByStatusVehicleId(VEHICLE)).thenReturn(List.of(indicator(status, cycleId)));
        when(fixture.reminders.findByVehicleIdAndMaintenanceTypeAndAuthoritativeCycleId(VEHICLE, "OIL", cycleId))
                .thenReturn(Optional.empty());
        when(fixture.reminders.findByVehicleIdAndMaintenanceTypeAndMaintenanceCycleReference(eq(VEHICLE), eq("OIL"), anyString()))
                .thenReturn(Optional.of(legacy));
        when(fixture.reminders.save(any(ReminderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.reevaluate(VEHICLE);

        assertEquals(cycleId, legacy.authoritativeCycleId);
        assertEquals("legacy-c1-reference", legacy.maintenanceCycleReference);
        verify(fixture.reminders).save(legacy);
        verifyNoInteractions(fixture.notifications, fixture.outbox);
    }

    @Test
    void aNewAuthoritativeCycleCreatesOneDistinctReminderWhileCompletedC1StaysTerminal() {
        Fixture fixture = new Fixture();
        VehicleMaintenanceStatusEntity status = status(2L);
        UUID c2 = UUID.randomUUID();
        when(fixture.statuses.findById(VEHICLE)).thenReturn(Optional.of(status));
        when(fixture.indicators.findByStatusVehicleId(VEHICLE)).thenReturn(List.of(indicator(status, c2)));
        VehicleReferenceEntity reference = reference();
        when(fixture.references.findById(VEHICLE)).thenReturn(Optional.of(reference));
        when(fixture.plans.findActive(eq("OIL"), any(LocalDate.class))).thenReturn(List.of(plan()));
        when(fixture.reminders.findByVehicleIdAndMaintenanceTypeAndAuthoritativeCycleId(VEHICLE, "OIL", c2))
                .thenReturn(Optional.empty());
        when(fixture.reminders.findByVehicleIdAndMaintenanceTypeAndMaintenanceCycleReference(eq(VEHICLE), eq("OIL"), anyString()))
                .thenReturn(Optional.empty());
        when(fixture.reminders.save(any(ReminderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.reevaluate(VEHICLE);

        var saved = org.mockito.ArgumentCaptor.forClass(ReminderEntity.class);
        verify(fixture.reminders, atLeastOnce()).save(saved.capture());
        assertEquals(c2, saved.getAllValues().getFirst().authoritativeCycleId);
        assertEquals(ReminderStatus.SCHEDULED, saved.getAllValues().getFirst().status);
        verify(fixture.notifications).save(any(ReminderNotificationEntity.class));
        verify(fixture.outbox).save(any(OutboxEventEntity.class));
    }

    @Test
    void c2WithTheSameLegacyDueOccurrenceCannotRebindCompletedC1() {
        Fixture fixture = new Fixture();
        VehicleMaintenanceStatusEntity status = status(3L);
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        ReminderEntity completedC1 = new ReminderEntity();
        completedC1.id = UUID.randomUUID();
        completedC1.status = ReminderStatus.COMPLETED;
        completedC1.authoritativeCycleId = c1;
        completedC1.maintenanceCycleReference = "legacy-collision";
        when(fixture.statuses.findById(VEHICLE)).thenReturn(Optional.of(status));
        when(fixture.indicators.findByStatusVehicleId(VEHICLE)).thenReturn(List.of(indicator(status, c2)));
        when(fixture.references.findById(VEHICLE)).thenReturn(Optional.of(reference()));
        when(fixture.plans.findActive(eq("OIL"), any(LocalDate.class))).thenReturn(List.of(plan()));
        when(fixture.reminders.findByVehicleIdAndMaintenanceTypeAndAuthoritativeCycleId(VEHICLE, "OIL", c2))
                .thenReturn(Optional.empty());
        when(fixture.reminders.findByVehicleIdAndMaintenanceTypeAndMaintenanceCycleReference(eq(VEHICLE), eq("OIL"), anyString()))
                .thenReturn(Optional.of(completedC1));
        when(fixture.reminders.save(any(ReminderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.reevaluate(VEHICLE);

        assertEquals(c1, completedC1.authoritativeCycleId);
        verify(fixture.reminders, never()).save(same(completedC1));
        var saved = org.mockito.ArgumentCaptor.forClass(ReminderEntity.class);
        verify(fixture.reminders, atLeastOnce()).save(saved.capture());
        ReminderEntity r2 = saved.getAllValues().getFirst();
        assertEquals(c2, r2.authoritativeCycleId);
        assertNotEquals(completedC1.maintenanceCycleReference, r2.maintenanceCycleReference);
        verify(fixture.notifications).save(any(ReminderNotificationEntity.class));
    }

    private static VehicleMaintenanceStatusEntity status(long sourceVersion) {
        VehicleMaintenanceStatusEntity status = new VehicleMaintenanceStatusEntity(VEHICLE);
        status.odometerKm = 25000;
        status.sourceVersion = sourceVersion;
        status.sourceTimestamp = SOURCE_TIME;
        status.payloadChecksum = "a".repeat(64);
        status.acceptedAt = SOURCE_TIME;
        return status;
    }

    private static MaintenanceIndicatorEntity indicator(VehicleMaintenanceStatusEntity status) {
        return indicator(status, null);
    }

    private static MaintenanceIndicatorEntity indicator(VehicleMaintenanceStatusEntity status, UUID cycleId) {
        MaintenanceIndicatorEntity indicator = new MaintenanceIndicatorEntity();
        indicator.id = UUID.randomUUID();
        indicator.status = status;
        indicator.indicatorCode = "OIL";
        indicator.dueOdometerKm = 25000L;
        indicator.dueDate = LocalDate.parse("2026-10-20");
        indicator.remainingDistanceKm = 0L;
        indicator.remainingDays = 11;
        indicator.severity = ReminderSeverity.WARNING;
        indicator.sourceTimestamp = SOURCE_TIME;
        indicator.maintenanceCycleId = cycleId;
        return indicator;
    }

    private static TelemetryMaintenanceStateEvent event(long version, UUID eventId) {
        return new TelemetryMaintenanceStateEvent(eventId, "maintenance-state", VEHICLE, SOURCE_TIME.plusSeconds(version), version,
                SOURCE_TIME, 25000, List.of(new TelemetryMaintenanceStateEvent.Indicator("OIL", 25000L,
                LocalDate.parse("2026-10-20"), 0L, 11, "WARNING", SOURCE_TIME)));
    }

    private static ReminderEntity completedCycle(Fixture fixture) {
        VehicleReferenceEntity reference = reference();
        MaintenancePlanEntity plan = plan();
        ReminderEntity reminder = new ReminderEntity();
        reminder.id = UUID.randomUUID();
        reminder.status = ReminderStatus.COMPLETED;
        when(fixture.references.findById(VEHICLE)).thenReturn(Optional.of(reference));
        when(fixture.plans.findActive(eq("OIL"), any(LocalDate.class))).thenReturn(List.of(plan));
        when(fixture.reminders.findByVehicleIdAndMaintenanceTypeAndMaintenanceCycleReference(eq(VEHICLE), eq("OIL"), anyString()))
                .thenReturn(Optional.of(reminder));
        return reminder;
    }

    private static VehicleReferenceEntity reference() {
        VehicleReferenceEntity reference = new VehicleReferenceEntity();
        reference.vehicleId = VEHICLE;
        reference.modelCode = "MODEL-X";
        reference.modelYear = 2024;
        reference.marketCode = "SG";
        reference.powertrainCode = "GASOLINE";
        return reference;
    }

    private static MaintenancePlanEntity plan() {
        MaintenancePlanEntity plan = mock(MaintenancePlanEntity.class);
        plan.id = UUID.randomUUID();
        plan.maintenanceType = "OIL";
        plan.applicabilityScopeIdentity = "sg-model-x";
        plan.modelCode = "MODEL-X";
        plan.marketCode = "SG";
        plan.powertrainCode = "GASOLINE";
        plan.severityPolicy = ReminderSeverity.WARNING;
        plan.policyVersion = "sg-model-x-v1";
        plan.validFrom = LocalDate.parse("2026-01-01");
        return plan;
    }

    private static final class Fixture {
        final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
        final VehicleMaintenanceStatusRepository statuses = mock(VehicleMaintenanceStatusRepository.class);
        final MaintenanceIndicatorRepository indicators = mock(MaintenanceIndicatorRepository.class);
        final VehicleReferenceRepository references = mock(VehicleReferenceRepository.class);
        final MaintenancePlanRepository plans = mock(MaintenancePlanRepository.class);
        final ReminderRepository reminders = mock(ReminderRepository.class);
        final ReminderNotificationRepository notifications = mock(ReminderNotificationRepository.class);
        final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        final MaintenanceService service = new MaintenanceService(processed, statuses, indicators, references, plans, reminders,
                notifications, outbox, new ObjectMapper().findAndRegisterModules());
    }
}
