package org.cvp.assistant.client.inventory;

import tools.jackson.databind.JsonNode;
import org.cvp.assistant.client.maintenance.UpstreamException;
import org.cvp.assistant.infrastructure.config.AssistantProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryHttpClient implements InventoryClientPort {
    private final RestClient client;
    public InventoryHttpClient(AssistantProperties properties) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Math.toIntExact(properties.clients().connectTimeout().toMillis()));
        requests.setReadTimeout(Math.toIntExact(properties.clients().readTimeout().toMillis()));
        this.client = RestClient.builder().baseUrl(properties.clients().inventoryBaseUrl()).requestFactory(requests).build();
    }
    @Override public JsonNode capability(String vehicleId) {
        try {
            return client.get().uri("/v1/vehicles/" + vehicleId + "/capability").retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> { throw new UpstreamException(response.getStatusCode().value(), "Inventory request failed"); })
                    .body(JsonNode.class);
        } catch (UpstreamException exception) { throw exception; }
        catch (RuntimeException exception) {
            boolean timeout = UpstreamException.isTimeout(exception);
            throw new UpstreamException(timeout ? 504 : 503, timeout ? "Inventory request timed out" : "Inventory is unavailable", timeout);
        }
    }
}
