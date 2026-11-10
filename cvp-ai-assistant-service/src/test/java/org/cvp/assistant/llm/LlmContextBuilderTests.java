package org.cvp.assistant.llm;

import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.rag.RagPassage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmContextBuilderTests {
    @Test
    void includesAllowListedMaintenanceFactsAndProvenance() {
        EvidenceSource maintenanceReminderSource = new EvidenceSource("maintenance-reminder-status-TEST-VEHICLE", "BUSINESS_API", "MAINTENANCE_REMINDER", "TEST-VEHICLE",
                Instant.now(), Instant.parse("2026-10-04T12:35:00Z"), "MAINTENANCE_REMINDER");
        ToolOutcome outcome = ToolOutcome.success("getMaintenanceStatus", "Maintenance status retrieved; reminder data retrieved",
                "maintenanceStatus: vehicleId=TEST-VEHICLE, odometerKm=14250, sourceTimestamp=2026-10-04T12:35:00Z, sourceVersion=2\n"
                        + "reminder: type=OIL, status=SCHEDULED, severity=WARNING, dueOdometerKm=15000, dueDate=2026-11-03, notificationStatus=DELIVERED",
                List.of(maintenanceReminderSource));

        String prompt = LlmContextBuilder.userMessage("What is upcoming?", "prior turn", List.of(outcome), List.of());

        assertTrue(prompt.contains("MAINTENANCE_REMINDER:MAINTENANCE_REMINDER"));
        assertTrue(prompt.contains("type=OIL"));
        assertTrue(prompt.contains("status=SCHEDULED"));
        assertTrue(prompt.contains("dueOdometerKm=15000"));
    }

    @Test
    void includesRetrievedRagChunkTextAndProvenance() {
        EvidenceSource rag = new EvidenceSource("chunk-singapore", "KNOWLEDGE_DOCUMENT", "RAG",
                "01-vehicle-maintenance-knowledge.md", Instant.now(), null, "APPROVED_DOCUMENT");
        RagPassage passage = new RagPassage(rag, "CVP Singapore states that a vehicle requires servicing every 12 months, or every 15,000 km, whichever comes first.");

        String prompt = LlmContextBuilder.userMessage("How often should a vehicle in Singapore be serviced?", "", List.of(), List.of(passage));

        assertTrue(prompt.contains("source=01-vehicle-maintenance-knowledge.md"));
        assertTrue(prompt.contains("provenance=APPROVED_DOCUMENT"));
        assertTrue(prompt.contains("12 months"));
        assertTrue(prompt.contains("15,000 km"));
    }
}
