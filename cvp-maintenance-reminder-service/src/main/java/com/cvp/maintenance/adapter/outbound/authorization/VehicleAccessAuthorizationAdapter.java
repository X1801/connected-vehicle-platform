package com.cvp.maintenance.adapter.outbound.authorization;

import com.cvp.maintenance.application.port.VehicleAccessPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class VehicleAccessAuthorizationAdapter implements VehicleAccessPort {
    private final RestClient client;

    public VehicleAccessAuthorizationAdapter(RestClient.Builder builder, @Value("${maintenance-reminder.integration.authorization.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public boolean mayAccess(String subject, String vehicleId) {
        AccessDecision response = client.get().uri(uri -> uri.path("/v1/access/vehicles/{vehicleId}")
                .queryParam("subject", subject).build(vehicleId)).retrieve().body(AccessDecision.class);
        return response != null && response.authorized();
    }

    public record AccessDecision(boolean authorized) { }
}
