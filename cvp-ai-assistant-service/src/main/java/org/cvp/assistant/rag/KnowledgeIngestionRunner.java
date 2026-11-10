package org.cvp.assistant.rag;

import org.cvp.assistant.infrastructure.config.AssistantProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;

@Configuration
@Profile("persistent")
class KnowledgeIngestionRunner {
    @Bean
    @ConditionalOnProperty(name = "assistant.rag.ingest-on-startup", havingValue = "true")
    CommandLineRunner ingestKnowledge(PostgresRag rag, AssistantProperties properties) {
        return args -> rag.ingest(Path.of(properties.rag().knowledgePath()));
    }
}
