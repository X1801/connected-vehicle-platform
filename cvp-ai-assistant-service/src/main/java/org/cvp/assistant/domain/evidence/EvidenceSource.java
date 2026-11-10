package org.cvp.assistant.domain.evidence;

import java.time.Instant;

public record EvidenceSource(String sourceId, String type, String service, String resourceId,
                             Instant retrievedAt, Instant sourceTimestamp, String provenance, String evidenceId) {
    /** Compatibility constructor for adapters that do not yet assign request-scoped IDs. */
    public EvidenceSource(String sourceId, String type, String service, String resourceId,
                          Instant retrievedAt, Instant sourceTimestamp, String provenance) {
        this(sourceId, type, service, resourceId, retrievedAt, sourceTimestamp, provenance, null);
    }

    public EvidenceSource withEvidenceId(String value) {
        return new EvidenceSource(sourceId, type, service, resourceId, retrievedAt, sourceTimestamp, provenance, value);
    }
}
