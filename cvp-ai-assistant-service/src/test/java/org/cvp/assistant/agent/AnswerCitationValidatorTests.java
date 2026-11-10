package org.cvp.assistant.agent;

import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.rag.RagPassage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerCitationValidatorTests {
    @Test
    void businessQuestionWithNoCitableEvidenceUsesServerOwnedFallback() {
        var result = AnswerCitationValidator.validatedOrFallback("What is my maintenance status?", "Your status is COMPLETED [E1]", List.of(), List.of());
        assertTrue(result.fallback());
        assertTrue(result.answer().startsWith("I could not retrieve verified evidence"));
        assertTrue(result.citationIds().isEmpty());
    }

    @Test
    void rejectsFabricatedAndFailedToolCitations() {
        ToolOutcome failed = ToolOutcome.failure("getMaintenanceStatus", "TIMEOUT", "unavailable", true);
        var result = AnswerCitationValidator.validatedOrFallback("maintenance status", "It is normal [E1]", List.of(failed), List.of());
        assertTrue(result.fallback());
        assertTrue(result.citationIds().isEmpty());

        ToolOutcome success = ToolOutcome.success("getMaintenanceStatus", "ok", "status=PENDING", List.of(source("E1")));
        var fabricated = AnswerCitationValidator.validatedOrFallback("maintenance status", "It is PENDING [E999]", List.of(success), List.of());
        assertTrue(fabricated.fallback());
        assertEquals(List.of("E1"), fabricated.citationIds());
    }

    @Test
    void rejectsUnsupportedCriticalClaimsButAllowsRetrievedRagCitation() {
        ToolOutcome success = ToolOutcome.success("getMaintenanceStatus", "ok", "status=PENDING,odometerKm=12000", List.of(source("E1")));
        var unsupported = AnswerCitationValidator.validatedOrFallback("maintenance status", "The odometer is 99999 [E1]", List.of(success), List.of());
        assertTrue(unsupported.fallback());

        RagPassage passage = new RagPassage(source("R1"), "Approved explanation");
        var explanation = AnswerCitationValidator.validatedOrFallback("Explain maintenance", "Here is an explanation [R1]", List.of(), List.of(passage));
        assertTrue(!explanation.fallback());
        assertEquals(List.of("R1"), explanation.citationIds());
    }

    @Test
    void greetingRemainsAvailableWithoutVehicleEvidence() {
        var result = AnswerCitationValidator.validatedOrFallback("Bonjour!", "unsafe unsupported answer", List.of(), List.of());
        assertTrue(result.fallback());
        assertEquals("Hello. I can help explain verified vehicle maintenance information when supporting evidence is available.", result.answer());
    }

    private EvidenceSource source(String id) {
        return new EvidenceSource("source-" + id, "BUSINESS_API", "maintenance-reminder", "TEST-VEHICLE", Instant.parse("2026-01-01T00:00:00Z"), null, "DIRECT", id);
    }
}
