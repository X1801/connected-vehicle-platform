package org.cvp.assistant.llm;

/** Untrusted LLM function-call proposal; AgentOrchestrator validates it before execution. */
public record ToolCall(String name, String vehicleId, boolean invalidArguments) {
    public ToolCall(String name, String vehicleId) { this(name, vehicleId, false); }
    public static ToolCall invalid(String name) { return new ToolCall(name, null, true); }
}
