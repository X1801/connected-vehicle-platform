package org.cvp.assistant.domain.evidence;

public record ToolError(String code, String message, boolean retryable) {
}
