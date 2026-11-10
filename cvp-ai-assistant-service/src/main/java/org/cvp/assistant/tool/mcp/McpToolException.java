package org.cvp.assistant.tool.mcp;
public class McpToolException extends RuntimeException { private final String code; private final boolean retryable; public McpToolException(String code, String message, boolean retryable) { super(message); this.code = code; this.retryable = retryable; } public String code() { return code; } public boolean retryable() { return retryable; } }
