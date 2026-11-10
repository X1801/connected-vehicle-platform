package com.cvp.maintenance.domain;

import java.time.Instant;

public final class SourceOrdering {
    public enum Decision {ACCEPT, STALE, IDENTICAL, CONFLICT}

    private SourceOrdering() {
    }

    public static Decision decide(Long incomingVersion, Instant incomingTimestamp, String incomingChecksum,
                                  Long storedVersion, Instant storedTimestamp, String storedChecksum) {
        if (storedVersion == null && storedTimestamp == null) return Decision.ACCEPT;
        if (incomingVersion != null && storedVersion != null) {
            if (incomingVersion < storedVersion) return Decision.STALE;
            if (incomingVersion > storedVersion) return Decision.ACCEPT;
            return incomingChecksum.equals(storedChecksum) ? Decision.IDENTICAL : Decision.CONFLICT;
        }
        if (incomingVersion == null && storedVersion == null) {
            if (incomingTimestamp.isBefore(storedTimestamp)) return Decision.STALE;
            if (incomingTimestamp.equals(storedTimestamp))
                return incomingChecksum.equals(storedChecksum) ? Decision.IDENTICAL : Decision.CONFLICT;
            return Decision.ACCEPT;
        }
        return incomingVersion != null ? Decision.ACCEPT : Decision.STALE;
    }
}
