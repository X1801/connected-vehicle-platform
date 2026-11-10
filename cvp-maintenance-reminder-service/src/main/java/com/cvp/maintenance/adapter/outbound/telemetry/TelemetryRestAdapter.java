package com.cvp.maintenance.adapter.outbound.telemetry;

import com.cvp.maintenance.adapter.inbound.kafka.TelemetryMaintenanceStateEvent;
import com.cvp.maintenance.application.port.TelemetryMaintenanceStateQueryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** Keeps the local/test Telemetry HTTP contract out of the domain and persistence model. */
@Component
public class TelemetryRestAdapter implements TelemetryMaintenanceStateQueryPort {
    private final RestClient client;

    public TelemetryRestAdapter(RestClient.Builder builder, @Value("${maintenance-reminder.integration.telemetry.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Optional<TelemetryMaintenanceStateEvent> findMaintenanceState(String vehicleId) {
        return Optional.ofNullable(client.get().uri("/v1/vehicles/{vehicleId}/maintenance-state", vehicleId)
                .accept(MediaType.APPLICATION_JSON).retrieve().body(TelemetryMaintenanceStateEvent.class));
    }
}
