package com.cvp.maintenance.domain;

import com.cvp.maintenance.adapter.outbound.persistence.AppointmentReminderEntity;
import com.cvp.maintenance.adapter.outbound.persistence.AppointmentReminderRepository;
import com.cvp.maintenance.adapter.outbound.persistence.ProcessedEventRepository;
import com.cvp.maintenance.adapter.outbound.persistence.ReminderEntity;
import com.cvp.maintenance.adapter.outbound.persistence.ReminderRepository;
import com.cvp.maintenance.application.AppointmentLifecycleService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentLifecycleServiceTest {
    private static final String VEHICLE = "TEST-VEHICLE";
    private static final UUID REMINDER_ID = UUID.randomUUID();

    @Test
    void competingBookedEventDoesNotCreateSecondUnfinishedAssociation() {
        var links = mock(AppointmentReminderRepository.class);
        var reminders = mock(ReminderRepository.class);
        var processed = mock(ProcessedEventRepository.class);
        var reminder = activeReminder();
        var existing = new AppointmentReminderEntity(UUID.randomUUID(), REMINDER_ID, VEHICLE,
                "OIL_SERVICE", "DMS-FIRST");
        when(reminders.findActiveForAppointment(VEHICLE, "OIL")).thenReturn(List.of(reminder));
        when(links.findByReminderIdAndCompletedAtIsNull(REMINDER_ID)).thenReturn(Optional.of(existing));

        service(links, reminders, processed).booked(bookedEvent(UUID.randomUUID(), "DMS-SECOND"));

        verify(links, never()).save(any());
        verify(processed).save(any());
    }

    @Test
    void bookedReplayAfterCompletedCycleIsAcknowledgedWithoutAssociation() {
        var links = mock(AppointmentReminderRepository.class);
        var reminders = mock(ReminderRepository.class);
        var processed = mock(ProcessedEventRepository.class);
        var completed = activeReminder();
        completed.status = ReminderStatus.COMPLETED;
        when(reminders.findActiveForAppointment(VEHICLE, "OIL")).thenReturn(List.of());
        when(reminders.findByVehicleIdAndMaintenanceType(VEHICLE, "OIL")).thenReturn(List.of(completed));

        assertDoesNotThrow(() -> service(links, reminders, processed).booked(bookedEvent(UUID.randomUUID(), "DMS-REPLAY")));

        verify(links, never()).save(any());
        verify(processed).save(any());
    }

    @Test
    void competingHistoricalCompletionCannotCloseAnotherAssociation() {
        var links = mock(AppointmentReminderRepository.class);
        var reminders = mock(ReminderRepository.class);
        var processed = mock(ProcessedEventRepository.class);
        UUID appointmentId = UUID.randomUUID();
        var link = new AppointmentReminderEntity(appointmentId, REMINDER_ID, VEHICLE, "OIL_SERVICE", "DMS-SECOND");
        var completed = activeReminder();
        completed.status = ReminderStatus.COMPLETED;
        when(links.findById(appointmentId)).thenReturn(Optional.of(link));
        when(reminders.findById(REMINDER_ID)).thenReturn(Optional.of(completed));

        service(links, reminders, processed).completed(completedEvent(appointmentId, "DMS-SECOND"));

        assertEquals(ReminderStatus.COMPLETED, completed.status);
        assertEquals(null, link.completedAt);
        verify(processed).save(any());
    }

    private AppointmentLifecycleService service(AppointmentReminderRepository links, ReminderRepository reminders,
                                                ProcessedEventRepository processed) {
        return new AppointmentLifecycleService(links, reminders, processed);
    }

    private ReminderEntity activeReminder() {
        var reminder = new ReminderEntity();
        reminder.id = REMINDER_ID;
        reminder.status = ReminderStatus.SCHEDULED;
        reminder.updatedAt = Instant.now();
        return reminder;
    }

    private AppointmentLifecycleService.Event bookedEvent(UUID appointmentId, String dmsReference) {
        return new AppointmentLifecycleService.Event(UUID.randomUUID(), "APPOINTMENT_BOOKED", appointmentId,
                VEHICLE, "DEALER", dmsReference, "OIL_SERVICE", "BOOKED");
    }

    private AppointmentLifecycleService.Event completedEvent(UUID appointmentId, String dmsReference) {
        return new AppointmentLifecycleService.Event(UUID.randomUUID(), "APPOINTMENT_COMPLETED", appointmentId,
                VEHICLE, "DEALER", dmsReference, "OIL_SERVICE", "COMPLETED");
    }
}
