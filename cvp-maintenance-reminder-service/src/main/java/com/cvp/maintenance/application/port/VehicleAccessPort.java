package com.cvp.maintenance.application.port;

/**
 * Dynamic ownership/delegation decision; no customer-master data is retained here.
 */
public interface VehicleAccessPort {
    boolean mayAccess(String subject, String vehicleId);
}
