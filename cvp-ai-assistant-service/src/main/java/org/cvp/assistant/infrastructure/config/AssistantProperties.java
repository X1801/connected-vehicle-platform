package org.cvp.assistant.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(Clients clients, Deepseek deepseek, Rag rag, Tools tools, Duration turnTimeout) {
    public record Clients(String maintenanceReminderBaseUrl, String telemetryBaseUrl, String inventoryBaseUrl,
                          Duration connectTimeout, Duration readTimeout) {
    }

    public record Deepseek(boolean enabled, String baseUrl, String model, String apiKey, Duration timeout) {
    }

    public record Rag(int topK, int embeddingDimensions, String knowledgePath) {
    }

    public record Tools(String transport, String mcpBaseUrl, Duration mcpTimeout) {
    }
}
