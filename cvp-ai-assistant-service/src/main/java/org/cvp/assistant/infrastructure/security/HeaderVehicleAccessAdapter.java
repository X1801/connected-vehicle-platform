package org.cvp.assistant.infrastructure.security;

import org.cvp.assistant.application.port.VehicleAccessPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Local replaceable access adapter. A production authorization provider implements the same port. */
@Component
public class HeaderVehicleAccessAdapter implements VehicleAccessPort {
    private final boolean allowAll;
    private final String allowedVehicles;

    public HeaderVehicleAccessAdapter(@Value("${assistant.vehicle-access.allow-all:false}") boolean allowAll,
                                      @Value("${assistant.vehicle-access.allowed-vehicles:}") String allowedVehicles) {
        this.allowAll = allowAll;
        this.allowedVehicles = allowedVehicles;
    }

    @Override
    public boolean canAccess(String principal, String vehicleId) {
        return principal != null && !principal.isBlank() && (allowAll || Arrays.stream(allowedVehicles.split(","))
                .map(String::trim).anyMatch(vehicleId::equals));
    }
}
