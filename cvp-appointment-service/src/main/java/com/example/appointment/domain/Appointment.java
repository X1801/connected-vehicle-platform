package com.example.appointment.domain;
import java.time.Instant; import java.util.UUID;
public record Appointment(UUID appointmentId, String vehicleId, String dealerId, String dealerName, String slotId, String serviceType, String partnerRequestReference, String externalDmsReference, AppointmentStatus status, Instant createdAt, Instant updatedAt, Instant bookedAt, Instant completedAt, long version) {}
