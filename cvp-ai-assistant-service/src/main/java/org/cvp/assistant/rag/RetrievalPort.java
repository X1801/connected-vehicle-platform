package org.cvp.assistant.rag;

import java.util.List;

public interface RetrievalPort {
    List<RagPassage> retrieve(String question, String vehicleId);
}
