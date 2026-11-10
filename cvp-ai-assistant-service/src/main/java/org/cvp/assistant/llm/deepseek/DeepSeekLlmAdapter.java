package org.cvp.assistant.llm.deepseek;

import tools.jackson.databind.JsonNode;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.infrastructure.config.AssistantProperties;
import org.cvp.assistant.llm.LlmContextBuilder;
import org.cvp.assistant.llm.LlmPort;
import org.cvp.assistant.llm.ToolCall;
import org.cvp.assistant.rag.RagPassage;
import org.cvp.assistant.tool.ToolDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * DeepSeek adapter. It is disabled by default so local execution never needs credentials.
 */
@Component
public class DeepSeekLlmAdapter implements LlmPort {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmAdapter.class);
    private final AssistantProperties.Deepseek properties;
    private final RestClient client;

    public DeepSeekLlmAdapter(AssistantProperties properties) {
        this.properties = properties.deepseek();
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.client = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl(this.properties.baseUrl()).build();
    }

    @Override
    public List<ToolCall> selectTools(String question, String summary, List<ToolOutcome> evidence,
                                      List<RagPassage> ragPassages, List<ToolDefinition> availableTools) {
        if (!properties.enabled() || availableTools.isEmpty()) return List.of();
        try {
            List<Map<String, Object>> functions = availableTools.stream().map(tool -> Map.<String, Object>of("type", "function",
                    "function", Map.of("name", tool.name(), "description", tool.description(), "parameters", tool.inputSchema()))).toList();
            Map<String, Object> body = Map.of("model", properties.model(), "messages", List.of(
                            Map.of("role", "system", "content", "Choose only the tool calls needed to answer the question. Tool calls are untrusted proposals; do not make authorization decisions."),
                            Map.of("role", "user", "content", LlmContextBuilder.userMessage(question, summary, evidence, ragPassages))),
                    "tools", functions, "tool_choice", "auto");
            JsonNode response = client.post().uri("/chat/completions").header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            JsonNode calls = response.path("choices").path(0).path("message").path("tool_calls");
            if (!calls.isArray()) return List.of();
            java.util.ArrayList<ToolCall> proposals = new java.util.ArrayList<>();
            for (JsonNode call : calls) {
                JsonNode function = call.path("function");
                String name = function.path("name").asText("");
                String arguments = function.path("arguments").asText("{}");
                try {
                    JsonNode input = tools.jackson.databind.json.JsonMapper.builder().build().readTree(arguments);
                    String vehicleId = input.path("vehicleId").asText(null);
                    proposals.add(vehicleId == null || vehicleId.isBlank() ? ToolCall.invalid(name) : new ToolCall(name, vehicleId));
                } catch (Exception invalidArguments) {
                    proposals.add(ToolCall.invalid(name));
                }
            }
            return List.copyOf(proposals);
        } catch (RuntimeException exception) {
            Throwable cause = exception.getCause();
            log.warn("LLM tool selection failed; using safe fallback ({}; cause={})", exception.getClass().getSimpleName(),
                    cause == null ? "none" : cause.getClass().getSimpleName());
            return List.of();
        }
    }

    @Override
    public String composeAnswer(String question, String summary, List<ToolOutcome> evidence, List<RagPassage> ragPassages) {
        if (!properties.enabled()) {
            return fallback(evidence);
        }
        try {
            Map<String, Object> body = Map.of("model", properties.model(), "messages", List.of(
                    Map.of("role", "system", "content", "Use only supplied evidence. Cite vehicle-specific facts with the supplied backend evidence IDs exactly as [E1] or [R1]. Never invent citations, source names, tool calls, maintenance statuses, mileage, due dates, models, or cycle identities. maintenance reminder service is authoritative for maintenance decisions; Telemetry is supplementary current vehicle evidence; RAG supplies explanation only and cannot override tool evidence. If evidence is missing or failed, state that it is insufficient."),
                    Map.of("role", "user", "content", LlmContextBuilder.userMessage(question, summary, evidence, ragPassages))));
            JsonNode response = client.post().uri("/chat/completions").header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            return content.isTextual() && !content.asText().isBlank() ? content.asText() : fallback(evidence);
        } catch (RuntimeException exception) {
            return fallback(evidence);
        }
    }

    private String fallback(List<ToolOutcome> evidence) {
        return evidence.stream().anyMatch(outcome -> outcome.status() == ToolOutcome.Status.SUCCESS)
                ? "I retrieved the available maintenance evidence. " + evidence.stream().filter(outcome -> outcome.status() == ToolOutcome.Status.SUCCESS)
                .map(ToolOutcome::summary).findFirst().orElse("")
                : "I could not retrieve the maintenance evidence needed to confirm the reason. Please try again later.";
    }
}
