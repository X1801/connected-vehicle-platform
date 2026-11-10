package org.cvp.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.cvp.mcp.client.DownstreamClient;
import org.cvp.mcp.tool.McpToolResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamClientIntegrationTests {
    private HttpServer server;
    private VehicleTools tools;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/vehicles/TEST-VEHICLE/maintenance-status", exchange -> json(exchange,
                "{\"vehicleId\":\"TEST-VEHICLE\",\"odometerKm\":14250,\"sourceVersion\":2}"));
        server.createContext("/api/v1/vehicles/TEST-VEHICLE/maintenance-reminders", exchange -> json(exchange,
                "[{\"id\":\"reminder-1\",\"maintenanceType\":\"OIL\",\"dueOdometerKm\":15000}]"));
        server.createContext("/v1/vehicles/TEST-VEHICLE/maintenance-state", exchange -> json(exchange,
                "{\"vehicleId\":\"TEST-VEHICLE\",\"eventType\":\"maintenance-state\",\"odometerKm\":10000}"));
        server.createContext("/v1/vehicles/TEST-VEHICLE/capability", exchange -> json(exchange,
                "{\"vehicleId\":\"TEST-VEHICLE\",\"model\":\"example-model\",\"modelYear\":2026,\"supportedCapabilities\":[\"maintenance-reminders\"]}"));
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        DownstreamClient client = new DownstreamClient(baseUrl, baseUrl, baseUrl, Duration.ofSeconds(1), new SimpleMeterRegistry());
        tools = new VehicleTools(client, client, client);
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    @Test
    void mapsMaintenanceStatusObjectAndReminderArrayWithoutStringifyingBusinessFields() {
        McpToolResponse response = tools.getMaintenanceStatus("TEST-VEHICLE");

        assertThat(response.status()).isEqualTo("SUCCESS");
        Map<String, Object> status = (Map<String, Object>) response.data().get("maintenanceStatus");
        List<Map<String, Object>> reminders = (List<Map<String, Object>>) response.data().get("reminders");
        assertThat(status).containsEntry("vehicleId", "TEST-VEHICLE").containsEntry("odometerKm", 14250);
        assertThat(reminders).singleElement().satisfies(reminder ->
                assertThat(reminder).containsEntry("maintenanceType", "OIL").containsEntry("dueOdometerKm", 15000));
    }

    @Test
    void callsTelemetryAndInventoryAtTheirConfiguredEndpointsAndMapsObjects() {
        McpToolResponse telemetry = tools.getVehicleMaintenanceState("TEST-VEHICLE");
        McpToolResponse inventory = tools.getVehicleCapability("TEST-VEHICLE");

        assertThat(telemetry.status()).isEqualTo("SUCCESS");
        assertThat(telemetry.data()).containsEntry("eventType", "maintenance-state").containsEntry("odometerKm", 10000);
        assertThat(inventory.status()).isEqualTo("SUCCESS");
        assertThat(inventory.data()).containsEntry("model", "example-model").containsEntry("modelYear", 2026);
        assertThat(inventory.data().get("supportedCapabilities")).isEqualTo(List.of("maintenance-reminders"));
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
