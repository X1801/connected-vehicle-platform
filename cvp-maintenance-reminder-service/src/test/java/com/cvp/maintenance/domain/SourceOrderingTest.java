package com.cvp.maintenance.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SourceOrderingTest {
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rejectsOlderAndConflictingVersions() {
        assertEquals(SourceOrdering.Decision.STALE, SourceOrdering.decide(1L, TIME, "a", 2L, TIME, "b"));
        assertEquals(SourceOrdering.Decision.CONFLICT, SourceOrdering.decide(2L, TIME, "a", 2L, TIME, "b"));
        assertEquals(SourceOrdering.Decision.IDENTICAL, SourceOrdering.decide(2L, TIME, "a", 2L, TIME, "a"));
    }

    @Test
    void acceptsHigherVersionCorrection() {
        assertEquals(SourceOrdering.Decision.ACCEPT, SourceOrdering.decide(3L, TIME.minusSeconds(60), "a", 2L, TIME, "b"));
    }
}
