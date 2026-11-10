package org.cvp.assistant.application.port;

public interface VehicleAccessPort {
    boolean canAccess(String principal, String vehicleId);
}
