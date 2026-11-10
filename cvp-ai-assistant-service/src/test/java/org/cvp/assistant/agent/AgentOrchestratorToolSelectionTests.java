package org.cvp.assistant.agent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.cvp.assistant.application.chat.ChatCommand;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.llm.LlmPort;
import org.cvp.assistant.llm.ToolCall;
import org.cvp.assistant.rag.RagPassage;
import org.cvp.assistant.rag.RetrievalPort;
import org.cvp.assistant.tool.AssistantTool;
import org.cvp.assistant.tool.ToolDefinition;
import org.cvp.assistant.tool.ToolExecutionContext;
import org.cvp.assistant.tool.TypedToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOrchestratorToolSelectionTests {
    @Test
    void executesLlmSelectedToolsThroughRegistryIncludingNonEnglishQuestion() {
        TrackingTool maintenanceReminderTool = new TrackingTool("getMaintenanceStatus");
        TrackingTool telemetry = new TrackingTool("getVehicleMaintenanceState");
        TrackingTool inventory = new TrackingTool("getVehicleCapability");
        FakeLlm llm = new FakeLlm(List.of(List.of(new ToolCall("getMaintenanceStatus", "TEST-VEHICLE")),
                List.of(new ToolCall("getVehicleMaintenanceState", "TEST-VEHICLE")),
                List.of(new ToolCall("getVehicleCapability", "TEST-VEHICLE"))));

        var result = agent(llm, 3, maintenanceReminderTool, telemetry, inventory).answer(command("¿Cuál es el estado actual del vehículo?"), "");

        assertEquals(List.of("getMaintenanceStatus", "getVehicleMaintenanceState", "getVehicleCapability"), maintenanceReminderAnd(telemetry, inventory, maintenanceReminderTool));
        assertEquals(3, result.toolResults().size());
        assertEquals(List.of("getMaintenanceStatus", "getVehicleCapability", "getVehicleMaintenanceState"),
                llm.availableTools.stream().map(ToolDefinition::name).toList());
        assertTrue(llm.availableTools.stream().allMatch(tool -> !tool.description().isBlank()
                && "object".equals(tool.inputSchema().get("type"))
                && ((java.util.Map<?, ?>) tool.inputSchema().get("properties")).containsKey("vehicleId")));
    }

    @Test
    void irrelevantQuestionUsesNoToolAndUnknownOrWrongVehicleCannotBypassPolicy() {
        TrackingTool maintenanceReminderTool = new TrackingTool("getMaintenanceStatus");
        FakeLlm noTools = new FakeLlm(List.of(List.of()));
        assertTrue(agent(noTools, 3, maintenanceReminderTool).answer(command("Bonjour"), "").toolResults().isEmpty());

        FakeLlm invalid = new FakeLlm(List.of(List.of(new ToolCall("notRegistered", "TEST-VEHICLE"),
                new ToolCall("getMaintenanceStatus", "OTHER-VEHICLE")), List.of()));
        var result = agent(invalid, 3, maintenanceReminderTool).answer(command("anything"), "");
        assertEquals(0, maintenanceReminderTool.calls);
        assertEquals("INVALID_ARGUMENT", result.toolResults().getFirst().error().code());
        assertEquals("FORBIDDEN", result.toolResults().get(1).error().code());
    }

    @Test
    void boundsRepeatedToolCallingRounds() {
        TrackingTool maintenanceReminderTool = new TrackingTool("getMaintenanceStatus");
        FakeLlm loop = new FakeLlm(List.of(List.of(new ToolCall("getMaintenanceStatus", null)),
                List.of(new ToolCall("getMaintenanceStatus", null)), List.of(new ToolCall("getMaintenanceStatus", null)),
                List.of(new ToolCall("getMaintenanceStatus", null))));
        agent(loop, 3, maintenanceReminderTool).answer(command("question"), "");
        assertEquals(3, maintenanceReminderTool.calls);
    }

    private AgentOrchestrator agent(FakeLlm llm, int rounds, TrackingTool... tools) {
        RetrievalPort rag = (question, vehicle) -> List.of();
        return new AgentOrchestrator(new TypedToolRegistry(List.of(tools)), rag, llm, new SimpleMeterRegistry(), Duration.ofSeconds(20), rounds);
    }

    private ChatCommand command(String question) { return new ChatCommand("user", "TEST-VEHICLE", question, null, "trace"); }
    private List<String> maintenanceReminderAnd(TrackingTool first, TrackingTool second, TrackingTool third) {
        List<String> names = new ArrayList<>();
        if (third.calls > 0) names.add(third.name()); if (first.calls > 0) names.add(first.name()); if (second.calls > 0) names.add(second.name());
        return names;
    }

    private static class TrackingTool implements AssistantTool<String> {
        private final String name; int calls;
        TrackingTool(String name) { this.name = name; }
        public String name() { return name; }
        public String description() { return name; }
        public ToolOutcome execute(ToolExecutionContext context, String input) {
            calls++; return ToolOutcome.success(name, "ok", "fact", List.of());
        }
    }

    private static class FakeLlm implements LlmPort {
        private final List<List<ToolCall>> rounds; private int round;
        private List<ToolDefinition> availableTools = List.of();
        FakeLlm(List<List<ToolCall>> rounds) { this.rounds = rounds; }
        public List<ToolCall> selectTools(String q, String c, List<ToolOutcome> e, List<RagPassage> r, List<ToolDefinition> d) {
            availableTools = List.copyOf(d);
            return round < rounds.size() ? rounds.get(round++) : List.of();
        }
        public String composeAnswer(String q, String c, List<ToolOutcome> e, List<RagPassage> r) { return "answer"; }
    }
}
