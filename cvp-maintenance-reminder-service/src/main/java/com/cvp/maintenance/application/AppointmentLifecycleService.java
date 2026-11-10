package com.cvp.maintenance.application;
import com.cvp.maintenance.adapter.outbound.persistence.AppointmentReminderEntity;
import com.cvp.maintenance.adapter.outbound.persistence.AppointmentReminderRepository;
import com.cvp.maintenance.adapter.outbound.persistence.ProcessedEventEntity;
import com.cvp.maintenance.adapter.outbound.persistence.ProcessedEventId;
import com.cvp.maintenance.adapter.outbound.persistence.ProcessedEventRepository;
import com.cvp.maintenance.adapter.outbound.persistence.ReminderRepository;
import com.cvp.maintenance.domain.ReminderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AppointmentLifecycleService {
    private final AppointmentReminderRepository links;
    private final ReminderRepository reminders;
    private final ProcessedEventRepository processed;

    public AppointmentLifecycleService(AppointmentReminderRepository links, ReminderRepository reminders,
                                       ProcessedEventRepository processed) {
        this.links = links;
        this.reminders = reminders;
        this.processed = processed;
    }

    @Transactional
    public void booked(Event event) {
        if (seen(event)) return;
        if (event.externalDmsReference() == null || event.externalDmsReference().isBlank())
            throw new IllegalArgumentException("Confirmed booking requires DMS reference");
        if (links.existsById(event.appointmentId())) {
            mark(event);
            return;
        }

        String maintenanceType = map(event.serviceType());
        var candidates = reminders.findActiveForAppointment(event.vehicleId(), maintenanceType);
        if (candidates.isEmpty() && reminders.findByVehicleIdAndMaintenanceType(event.vehicleId(), maintenanceType)
                .stream().anyMatch(reminder -> reminder.status == ReminderStatus.COMPLETED)) {
            // A replay from an already completed cycle is terminal, not retriable.
            mark(event);
            return;
        }
        if (candidates.size() != 1) throw new IllegalStateException("Expected exactly one active reminder");
        if (links.findByReminderIdAndCompletedAtIsNull(candidates.getFirst().id).isPresent()) {
            mark(event);
            return;
        }
        links.save(new AppointmentReminderEntity(event.appointmentId(), candidates.getFirst().id,
                event.vehicleId(), event.serviceType(), event.externalDmsReference()));
        mark(event);
    }

    @Transactional
    public void completed(Event event) {
        if (seen(event)) return;
        var link = links.findById(event.appointmentId())
                .orElseThrow(() -> new IllegalStateException("No booking association"));
        if (!link.vehicleId.equals(event.vehicleId()) || !link.serviceType.equals(event.serviceType())
                || !link.externalDmsReference.equals(event.externalDmsReference()))
            throw new IllegalStateException("Mismatched appointment completion");

        var reminder = reminders.findById(link.reminderId).orElseThrow();
        if (link.completedAt == null && reminder.status != ReminderStatus.COMPLETED) {
            reminder.status = ReminderStatus.COMPLETED;
            reminder.nextActionAt = null;
            reminder.updatedAt = Instant.now();
            link.completedAt = Instant.now();
        }
        mark(event);
    }

    private boolean seen(Event event) {
        return processed.existsById(new ProcessedEventId("APPOINTMENT", event.eventId(), "appointment-lifecycle"));
    }

    private void mark(Event event) {
        processed.save(new ProcessedEventEntity(
                new ProcessedEventId("APPOINTMENT", event.eventId(), "appointment-lifecycle"), "0".repeat(64)));
    }

    private String map(String serviceType) {
        if ("OIL_SERVICE".equals(serviceType)) return "OIL";
        throw new IllegalArgumentException("Unsupported appointment service type: " + serviceType);
    }

    public record Event(UUID eventId, String eventType, UUID appointmentId, String vehicleId, String dealerId,
                        String externalDmsReference, String serviceType, String status) {
    }
}
