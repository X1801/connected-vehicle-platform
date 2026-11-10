package org.cvp.assistant.rag;

import org.cvp.assistant.domain.evidence.EvidenceSource;

/** A bounded, attributable knowledge passage supplied to answer composition. */
public record RagPassage(EvidenceSource source, String text) { }
