package org.cvp.assistant.rag;

/** Application-owned seam for replacing the deterministic Phase 1 embedding model. */
public interface EmbeddingPort {
    double[] embed(String text);
}
