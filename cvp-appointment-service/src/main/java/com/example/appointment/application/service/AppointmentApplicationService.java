package com.example.appointment.application.service;

import com.example.appointment.application.*;
import com.example.appointment.application.port.in.*;
import com.example.appointment.application.port.out.*;
import com.example.appointment.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AppointmentApplicationService implements CreateAppointmentUseCase, ConfirmAppointmentUseCase, CompleteAppointmentUseCase {
    private final AppointmentRepositoryPort repository;
    private final PartnerIntegrationPort partner;
    private final AppointmentEventPort events;

    public AppointmentApplicationService(AppointmentRepositoryPort repository, PartnerIntegrationPort partner, AppointmentEventPort events) {
        this.repository = repository;
        this.partner = partner;
        this.events = events;
    }

    @Transactional
    public Appointment create(CreateAppointmentCommand c) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        var a = new Appointment(id, c.vehicleId(), c.dealerId(), c.dealerName(), c.slotId(), c.serviceType(), null, null, AppointmentStatus.PENDING, now, now, null, null, 0);
        var r = partner.submitBooking(new PartnerIntegrationPort.PartnerBookingRequest(id, c.vehicleId(), c.dealerId(), c.dealerName(), c.slotId(), c.serviceType()));
        if (!r.accepted())
            throw new PartnerUnavailableException("Partner Integration rejected booking submission", null);
        return repository.save(new Appointment(id, a.vehicleId(), a.dealerId(), a.dealerName(), a.slotId(), a.serviceType(), r.partnerRequestReference(), null, a.status(), a.createdAt(), a.updatedAt(), null, null, 0));
    }

    @Transactional
    public Appointment confirm(UUID id, String ref) {
        var a = get(id);
        validateReference(a, ref);
        if (a.status() == AppointmentStatus.BOOKED) return a;
        if (a.status() != AppointmentStatus.PENDING)
            throw new ConflictException("Appointment cannot be confirmed from " + a.status());
        Instant now = Instant.now();
        var saved = repository.save(new Appointment(a.appointmentId(), a.vehicleId(), a.dealerId(), a.dealerName(), a.slotId(), a.serviceType(), a.partnerRequestReference(), ref, AppointmentStatus.BOOKED, a.createdAt(), now, now, a.completedAt(), a.version()));
        events.publishBooked(saved);
        return saved;
    }

    @Transactional
    public Appointment complete(UUID id, String ref) {
        var a = get(id);
        validateReference(a, ref);
        if (a.status() == AppointmentStatus.COMPLETED) return a;
        if (a.status() != AppointmentStatus.BOOKED)
            throw new ConflictException("Appointment cannot be completed from " + a.status());
        Instant now = Instant.now();
        var saved = repository.save(new Appointment(a.appointmentId(), a.vehicleId(), a.dealerId(), a.dealerName(), a.slotId(), a.serviceType(), a.partnerRequestReference(), a.externalDmsReference() != null ? a.externalDmsReference() : ref, AppointmentStatus.COMPLETED, a.createdAt(), now, a.bookedAt(), now, a.version()));
        events.publishCompleted(saved);
        return saved;
    }

    public Appointment get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Appointment not found: " + id));
    }

    private void validateReference(Appointment a, String ref) {
        if (ref != null && a.externalDmsReference() != null && !a.externalDmsReference().equals(ref))
            throw new ConflictException("Conflicting external DMS reference");
    }
}
