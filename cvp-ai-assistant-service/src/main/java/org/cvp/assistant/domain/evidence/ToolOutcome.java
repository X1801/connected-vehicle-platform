package org.cvp.assistant.domain.evidence;

import java.util.List;

public record ToolOutcome(String tool, Status status, String summary, String facts, List<EvidenceSource> sources, ToolError error) {
    public enum Status { SUCCESS, PARTIAL, ERROR }

    public static ToolOutcome success(String tool, String summary, List<EvidenceSource> sources) {
        return new ToolOutcome(tool, Status.SUCCESS, summary, "", sources, null);
    }

    public static ToolOutcome success(String tool, String summary, String facts, List<EvidenceSource> sources) {
        return new ToolOutcome(tool, Status.SUCCESS, summary, facts, sources, null);
    }

    public static ToolOutcome failure(String tool, String code, String summary, boolean retryable) {
        return new ToolOutcome(tool, Status.ERROR, summary, "", List.of(), new ToolError(code, summary, retryable));
    }
}
