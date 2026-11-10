package com.cvp.maintenance.application.port;

/**
 * Phase 1 boundary only. Appointment booking is intentionally not modelled.
 */
public interface AppointmentAvailabilityPort {
    boolean isAvailable(String vehicleId);
}
