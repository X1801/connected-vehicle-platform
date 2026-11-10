package org.cvp.assistant.tool.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.cvp.assistant.infrastructure.config.AssistantProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates a Streamable HTTP session using the official MCP Java SDK.
 */
@Component
final class OfficialMcpClientSessionFactory implements McpClientSessionFactory {
    private final AssistantProperties properties;

    OfficialMcpClientSessionFactory(AssistantProperties properties) {
        this.properties = properties;
    }

    @Override
    public McpClientSession open() {
        var timeout = properties.tools().mcpTimeout();
        var transport = WebClientStreamableHttpTransport.builder(WebClient.builder())
                .endpoint(properties.tools().mcpBaseUrl()).openConnectionOnStartup(false).build();
        var client = McpClient.sync(transport).requestTimeout(timeout).initializationTimeout(timeout)
                .clientInfo(new McpSchema.Implementation("cvp-ai-assistant", "phase-2")).build();
        return new OfficialMcpClientSession(client);
    }

    private static final class OfficialMcpClientSession implements McpClientSession {
        private final McpSyncClient client;

        private OfficialMcpClientSession(McpSyncClient client) {
            this.client = client;
        }

        @Override
        public void initialize() {
            client.initialize();
        }

        @Override
        public Set<String> listToolNames() {
            return client.listTools().tools().stream().map(McpSchema.Tool::name).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public McpToolResult callTool(String name, Map<String, Object> arguments) {
            var response = client.callTool(new McpSchema.CallToolRequest(name, arguments));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("content", response.content());
            payload.put("isError", Boolean.TRUE.equals(response.isError()));
            if (response.structuredContent() != null) payload.put("structuredContent", response.structuredContent());
            return new McpToolResult(Boolean.TRUE.equals(response.isError()), payload);
        }

        @Override
        public void close() {
            client.closeGracefully();
        }
    }
}
