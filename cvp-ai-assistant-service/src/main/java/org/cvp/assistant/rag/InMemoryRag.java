package org.cvp.assistant.rag;

import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Phase 1 replacement boundary for approved-document ingestion and vector retrieval. */
@Component
@Profile("!persistent")
public class InMemoryRag implements RetrievalPort {
    @Override
    public List<RagPassage> retrieve(String question, String vehicleId) {
        if (question.toLowerCase().contains("manual") || question.toLowerCase().contains("maintenance")) {
            EvidenceSource source = new EvidenceSource("manual-maintenance-v1", "KNOWLEDGE_DOCUMENT", "RAG", "manual-maintenance-v1",
                    Instant.now(), null, "APPROVED_DOCUMENT");
            return List.of(new RagPassage(source, "RAG is explanatory evidence only; the maintenance reminder service remains authoritative for maintenance decisions."));
        }
        return List.of();
    }
}
