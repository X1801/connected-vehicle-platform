package org.cvp.assistant.tool.mcp;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpProtocolClientTests {
    private static final Set<String> TOOLS = Set.of("getMaintenanceStatus", "getVehicleMaintenanceState", "getVehicleCapability");

    @Test
    void initializesListsAndCallsOnceThenReusesTheOfficialSession() {
        FakeSession session = new FakeSession(TOOLS, new McpToolResult(false, Map.of("content", "maintenance result")));
        AtomicInteger opens = new AtomicInteger();
        McpProtocolClient client = new McpProtocolClient(() -> {
            opens.incrementAndGet();
            return session;
        });

        JsonNode first = client.call("getMaintenanceStatus", "TEST-VEHICLE", "correlation-1");
        client.call("getVehicleCapability", "TEST-VEHICLE", "correlation-2");

        assertThat(opens).hasValue(1);
        assertThat(session.initializeCalls).isEqualTo(1);
        assertThat(session.listCalls).isEqualTo(1);
        assertThat(session.calls).isEqualTo(2);
        assertThat(session.lastArguments).containsEntry("vehicleId", "TEST-VEHICLE");
        assertThat(first.path("content").asString()).isEqualTo("maintenance result");
    }

    @Test
    void rejectsToolMissingFromOfficialDiscovery() {
        FakeSession session = new FakeSession(Set.of("getMaintenanceStatus"), new McpToolResult(false, Map.of()));
        McpProtocolClient client = new McpProtocolClient(() -> session);

        assertThatThrownBy(() -> client.call("getMaintenanceStatus", "TEST-VEHICLE", "c"))
                .isInstanceOf(McpToolException.class).hasMessageContaining("missing required");
        assertThat(session.closed).isTrue();
    }

    @Test
    void mapsMcpToolFailureWithoutTreatingItAsAProtocolFailure() {
        FakeSession failed = new FakeSession(TOOLS, new McpToolResult(true, Map.of("content", "downstream unavailable")));
        McpProtocolClient client = new McpProtocolClient(() -> failed);

        assertThatThrownBy(() -> client.call("getMaintenanceStatus", "TEST-VEHICLE", "c"))
                .isInstanceOf(McpToolException.class).hasMessageContaining("downstream invocation failed");
        assertThat(failed.closed).isFalse();
    }

    @Test
    void mapsOfficialClientTransportFailure() {
        McpProtocolClient client = new McpProtocolClient(() -> {
            throw new IllegalStateException("connection failed");
        });

        assertThatThrownBy(() -> client.call("getMaintenanceStatus", "TEST-VEHICLE", "c"))
                .isInstanceOf(McpToolException.class).hasMessageContaining("initialization failed");
    }

    private static final class FakeSession implements McpClientSession {
        private final Set<String> tools;
        private final McpToolResult result;
        private int initializeCalls;
        private int listCalls;
        private int calls;
        private boolean closed;
        private Map<String, Object> lastArguments = new LinkedHashMap<>();

        private FakeSession(Set<String> tools, McpToolResult result) {
            this.tools = tools;
            this.result = result;
        }

        @Override
        public void initialize() {
            initializeCalls++;
        }

        @Override
        public Set<String> listToolNames() {
            listCalls++;
            return tools;
        }

        @Override
        public McpToolResult callTool(String name, Map<String, Object> arguments) {
            calls++;
            lastArguments = arguments;
            return result;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
