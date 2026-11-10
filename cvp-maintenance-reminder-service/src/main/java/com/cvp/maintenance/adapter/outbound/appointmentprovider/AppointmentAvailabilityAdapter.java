package com.cvp.maintenance.adapter.outbound.appointmentprovider;

import com.cvp.maintenance.application.port.AppointmentAvailabilityPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Isolated Phase 1 contract; no call site intentionally exists until Phase 2 is approved. */
@Component
public class AppointmentAvailabilityAdapter implements AppointmentAvailabilityPort {
    private final RestClient client;

    public AppointmentAvailabilityAdapter(RestClient.Builder builder, @Value("${maintenance-reminder.integration.appointment-provider.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public boolean isAvailable(String vehicleId) {
        Availability response = client.get().uri("/v1/appointments/availability?vehicleId={vehicleId}", vehicleId)
                .retrieve().body(Availability.class);
        return response != null && response.available();
    }

    public record Availability(boolean available) { }
}
