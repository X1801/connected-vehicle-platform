package org.cvp.mcp.client;

import io.micrometer.core.instrument.MeterRegistry;
import org.cvp.mcp.tool.McpToolResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit adapters for maintenance reminders, Telemetry and Inventory, with bounded calls and controlled failures.
 */
@Component
public class DownstreamClient {
    private final Map<String, RestClient> clients;
    private final MeterRegistry meters;
    private final String selected;
    private final JsonMapper jsonMapper;

    @Autowired
    public DownstreamClient(@Value("${mcp.maintenance-reminder-base-url}") String maintenanceReminderUrl,
                            @Value("${mcp.telemetry-base-url}") String telemetry,
                            @Value("${mcp.inventory-base-url}") String inventory,
                            @Value("${mcp.downstream.timeout}") Duration timeout,
                            MeterRegistry meters) {
        this(Map.of("maintenance-reminder", client(maintenanceReminderUrl, timeout), "telemetry", client(telemetry, timeout), "inventory", client(inventory, timeout)), meters, null);
    }

    private DownstreamClient(Map<String, RestClient> clients, MeterRegistry meters, String selected) {
        this.clients = clients;
        this.meters = meters;
        this.selected = selected;
        this.jsonMapper = JsonMapper.builder().build();
    }

    public DownstreamClient named(String name) {
        return new DownstreamClient(clients, meters, name);
    }

    public McpToolResponse getMaintenance(String vehicleId) {
        if (invalidVehicleId(vehicleId)) return failure("INVALID_ARGUMENT", "vehicleId is required", false);
        try {
            JsonNode status = request("/api/v1/vehicles/{vehicleId}/maintenance-status", vehicleId);
            if (!status.isObject()) return malformed("Maintenance status must be a JSON object");
            JsonNode reminders = request("/api/v1/vehicles/{vehicleId}/maintenance-reminders?size=20", vehicleId);
            if (!reminders.isArray()) return malformed("Maintenance reminders must be a JSON array");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("maintenanceStatus", jsonMapper.readValue(status.toString(), Map.class));
            data.put("reminders", jsonMapper.readValue(reminders.toString(), List.class));
            return McpToolResponse.success(service(), data);
        } catch (DownstreamException e) {
            return failure(code(e.status), e.getMessage(), e.status >= 500 || e.status == 429);
        } catch (RuntimeException e) {
            return failure("SERVICE_UNAVAILABLE", "Downstream service is unavailable", true);
        }
    }

    public McpToolResponse get(String uri, String vehicleId) {
        if (invalidVehicleId(vehicleId)) return failure("INVALID_ARGUMENT", "vehicleId is required", false);
        try {
            JsonNode response = request(uri, vehicleId);
            if (!response.isObject()) return malformed("Downstream response must be a JSON object");
            return McpToolResponse.success(service(), jsonMapper.readValue(response.toString(), Map.class));
        } catch (DownstreamException e) {
            return failure(code(e.status), e.getMessage(), e.status >= 500 || e.status == 429);
        } catch (RuntimeException e) {
            return failure("SERVICE_UNAVAILABLE", "Downstream service is unavailable", true);
        }
    }

    private JsonNode request(String uri, String vehicleId) {
        long start = System.nanoTime();
        try {
            JsonNode response = clients.get(selected).get().uri(uri, vehicleId).headers(headers -> {
                        String correlationId = correlationId();
                        if (correlationId != null) headers.set("X-Correlation-Id", correlationId);
                    }).retrieve().onStatus(HttpStatusCode::isError,
                            (request, errorResponse) -> {
                                throw new DownstreamException(errorResponse.getStatusCode().value(), "Downstream request failed");
                            })
                    .body(JsonNode.class);
            if (response == null) throw new IllegalStateException("Downstream response was empty");
            meters.counter("mcp.downstream.calls", "service", service(), "outcome", "success").increment();
            return response;
        } finally {
            meters.timer("mcp.downstream.duration", "service", service()).record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private McpToolResponse malformed(String message) {
        return failure("MALFORMED_RESPONSE", message, false);
    }

    private McpToolResponse failure(String code, String message, boolean retryable) {
        meters.counter("mcp.downstream.errors", "service", service(), "code", code).increment();
        return McpToolResponse.failure(service(), code, message, retryable);
    }

    private boolean invalidVehicleId(String vehicleId) {
        return vehicleId == null || vehicleId.isBlank();
    }

    private String service() {
        return selected == null ? "UNKNOWN" : selected.toUpperCase();
    }

    private static String code(int status) {
        return status == 404 ? "NOT_FOUND" : status >= 500 ? "UPSTREAM_UNAVAILABLE" : "DOWNSTREAM_" + status;
    }

    private static RestClient client(String url, Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    private static String correlationId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest().getHeader("X-Correlation-Id") : null;
    }

    private static final class DownstreamException extends RuntimeException {
        private final int status;

        private DownstreamException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
