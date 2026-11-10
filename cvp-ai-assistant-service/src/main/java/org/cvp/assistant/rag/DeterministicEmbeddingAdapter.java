package org.cvp.assistant.rag;

import org.cvp.assistant.infrastructure.config.AssistantProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Offline, repeatable embedding baseline; swap this adapter for a provider without changing ingestion or retrieval. */
@Component
@Profile("persistent")
public class DeterministicEmbeddingAdapter implements EmbeddingPort {
    private final int dimensions;

    public DeterministicEmbeddingAdapter(AssistantProperties properties) {
        this.dimensions = properties.rag().embeddingDimensions();
    }

    @Override
    public double[] embed(String text) {
        double[] vector = new double[dimensions];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
            if (!token.isBlank()) vector[Math.floorMod(token.hashCode(), dimensions)] += 1d;
        }
        double length = 0d;
        for (double value : vector) length += value * value;
        if (length == 0d) return vector;
        length = Math.sqrt(length);
        for (int i = 0; i < vector.length; i++) vector[i] /= length;
        return vector;
    }
}
