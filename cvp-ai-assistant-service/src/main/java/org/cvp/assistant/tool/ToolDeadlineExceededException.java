package org.cvp.assistant.tool;

public class ToolDeadlineExceededException extends RuntimeException {
    public ToolDeadlineExceededException() { super("Tool execution deadline was exceeded"); }
}
