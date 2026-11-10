package com.example.appointment.application.service;

import com.example.appointment.application.*;
import com.example.appointment.application.port.in.CreateAppointmentUseCase.CreateAppointmentCommand;
import com.example.appointment.application.port.out.*;
import com.example.appointment.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AppointmentApplicationServiceTest {
    @Test void createStaysPendingAndStoresOnlyPartnerRequestReference() {
        var repo = new InMemoryRepository(); var events = new Events();
        var service = new AppointmentApplicationService(repo, request -> new PartnerIntegrationPort.PartnerSubmissionResult(true, "REQUEST-1"), events);
        var appointment = service.create(command());
        assertEquals(AppointmentStatus.PENDING, appointment.status());
        assertEquals("REQUEST-1", appointment.partnerRequestReference());
        assertNull(appointment.externalDmsReference()); assertTrue(events.types.isEmpty());
    }
    @Test void confirmationIsIdempotentAndEmitsOneEvent() {
        var repo = new InMemoryRepository(); var events = new Events(); var service = service(repo, events);
        var pending = service.create(command());
        var booked = service.confirm(pending.appointmentId(), "DMS-1"); service.confirm(pending.appointmentId(), "DMS-1");
        assertEquals(AppointmentStatus.BOOKED, booked.status()); assertNotNull(booked.bookedAt()); assertEquals(List.of("booked"), events.types);
    }
    @Test void completionRequiresBookingAndIsIdempotent() {
        var repo = new InMemoryRepository(); var events = new Events(); var service = service(repo, events); var pending = service.create(command());
        assertThrows(ConflictException.class, () -> service.complete(pending.appointmentId(), "DMS-1"));
        service.confirm(pending.appointmentId(), "DMS-1"); var completed = service.complete(pending.appointmentId(), "DMS-1"); service.complete(pending.appointmentId(), "DMS-1");
        assertEquals(AppointmentStatus.COMPLETED, completed.status()); assertNotNull(completed.completedAt()); assertEquals(List.of("booked", "completed"), events.types);
    }
    @Test void conflictingDmsReferenceIsRejectedAndPreserved() {
        var repo = new InMemoryRepository(); var service = service(repo, new Events()); var pending = service.create(command()); service.confirm(pending.appointmentId(), "DMS-1");
        assertThrows(ConflictException.class, () -> service.complete(pending.appointmentId(), "DMS-2")); assertEquals("DMS-1", repo.findById(pending.appointmentId()).orElseThrow().externalDmsReference());
    }
    private AppointmentApplicationService service(InMemoryRepository repo, Events events) { return new AppointmentApplicationService(repo, r -> new PartnerIntegrationPort.PartnerSubmissionResult(true, "REQ"), events); }
    private CreateAppointmentCommand command() { return new CreateAppointmentCommand("TEST-VEHICLE", "DEALER-001", "Central Service Centre", "SLOT-1001", "OIL_SERVICE"); }
    static class InMemoryRepository implements AppointmentRepositoryPort { final Map<UUID,Appointment> values = new HashMap<>(); public Appointment save(Appointment a) { values.put(a.appointmentId(), a); return a; } public Optional<Appointment> findById(UUID id) { return Optional.ofNullable(values.get(id)); } }
    static class Events implements AppointmentEventPort { final List<String> types = new ArrayList<>(); public void publishBooked(Appointment a) { types.add("booked"); } public void publishCompleted(Appointment a) { types.add("completed"); } }
}
