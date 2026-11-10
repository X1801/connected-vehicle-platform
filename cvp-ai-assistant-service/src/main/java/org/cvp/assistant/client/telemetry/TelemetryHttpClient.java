package org.cvp.assistant.client.telemetry;

import tools.jackson.databind.JsonNode;
import org.cvp.assistant.client.maintenance.UpstreamException;
import org.cvp.assistant.infrastructure.config.AssistantProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TelemetryHttpClient implements TelemetryMaintenanceStateClientPort {
    private final RestClient client;
    public TelemetryHttpClient(AssistantProperties properties) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Math.toIntExact(properties.clients().connectTimeout().toMillis()));
        requests.setReadTimeout(Math.toIntExact(properties.clients().readTimeout().toMillis()));
        this.client = RestClient.builder().baseUrl(properties.clients().telemetryBaseUrl()).requestFactory(requests).build();
    }
    @Override public JsonNode maintenanceState(String vehicleId) {
        try {
            return client.get().uri("/v1/vehicles/" + vehicleId + "/maintenance-state").retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> { throw new UpstreamException(response.getStatusCode().value(), "Telemetry request failed"); })
                    .body(JsonNode.class);
        } catch (UpstreamException exception) { throw exception; }
        catch (RuntimeException exception) {
            boolean timeout = UpstreamException.isTimeout(exception);
            throw new UpstreamException(timeout ? 504 : 503, timeout ? "Telemetry request timed out" : "Telemetry is unavailable", timeout);
        }
    }
}
