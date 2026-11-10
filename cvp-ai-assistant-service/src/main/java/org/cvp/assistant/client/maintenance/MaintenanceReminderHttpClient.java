package org.cvp.assistant.client.maintenance;

import tools.jackson.databind.JsonNode;
import org.cvp.assistant.infrastructure.config.AssistantProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MaintenanceReminderHttpClient implements MaintenanceReminderClientPort {
    private final RestClient client;

    public MaintenanceReminderHttpClient(AssistantProperties properties) {
        this.client = client(properties, properties.clients().maintenanceReminderBaseUrl());
    }

    @Override
    public JsonNode maintenanceStatus(String vehicleId) {
        return get("/api/v1/vehicles/" + vehicleId + "/maintenance-status");
    }

    @Override
    public JsonNode reminders(String vehicleId) {
        return get("/api/v1/vehicles/" + vehicleId + "/maintenance-reminders?size=20");
    }

    @Override
    public JsonNode reminderDetail(String reminderId) {
        return get("/api/v1/maintenance-reminders/" + reminderId);
    }

    private JsonNode get(String uri) {
        try {
            return client.get().uri(uri).retrieve().onStatus(HttpStatusCode::isError, (request, response) -> {
                throw new UpstreamException(response.getStatusCode().value(), "maintenance reminder service request failed");
            }).body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new UpstreamException(exception.getStatusCode().value(), "maintenance reminder service request failed");
        } catch (UpstreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new UpstreamException(UpstreamException.isTimeout(exception) ? 504 : 503,
                    UpstreamException.isTimeout(exception) ? "maintenance reminder service request timed out" : "maintenance reminder service is unavailable",
                    UpstreamException.isTimeout(exception));
        }
    }

    private RestClient client(AssistantProperties properties, String baseUrl) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Math.toIntExact(properties.clients().connectTimeout().toMillis()));
        requests.setReadTimeout(Math.toIntExact(properties.clients().readTimeout().toMillis()));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }
}
