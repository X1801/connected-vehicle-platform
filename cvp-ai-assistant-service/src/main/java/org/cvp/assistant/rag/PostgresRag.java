package org.cvp.assistant.rag;

import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.infrastructure.config.AssistantProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** PostgreSQL-backed, bounded semantic retrieval for approved RAG knowledge. */
@Component
@Profile("persistent")
public class PostgresRag implements RetrievalPort {
    private static final Pattern NAMED_TERM = Pattern.compile("\\b[A-Z][a-z]{2,}\\b");
    private final JdbcTemplate jdbc;
    private final EmbeddingPort embeddings;
    private final AssistantProperties.Rag properties;

    public PostgresRag(JdbcTemplate jdbc, EmbeddingPort embeddings, AssistantProperties properties) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.properties = properties.rag();
    }

    @Override
    public List<RagPassage> retrieve(String question, String vehicleId) {
        String vector = postgresArray(embeddings.embed(question));
        String namedTerm = namedTerm(question);
        return jdbc.query("""
                SELECT c.id, d.source_name, c.chunk_text, c.updated_at,
                       (SELECT sum(a * b) FROM unnest(c.embedding, CAST(? AS float8[])) AS u(a, b)) AS score
                FROM rag_chunk c JOIN rag_document d ON d.id = c.document_id
                ORDER BY (CASE WHEN ? <> '' AND lower(c.chunk_text) LIKE '%' || lower(?) || '%' THEN 10 ELSE 0 END)
                         + (0.7 * ts_rank_cd(to_tsvector('english', c.chunk_text), websearch_to_tsquery('english', ?))
                         + 0.3 * COALESCE((SELECT sum(a * b) FROM unnest(c.embedding, CAST(? AS float8[])) AS u(a, b)), 0)) DESC,
                         c.chunk_index ASC LIMIT ?
                """, (rs, row) -> new RagPassage(
                new EvidenceSource(rs.getString("id"), "KNOWLEDGE_DOCUMENT", "RAG", rs.getString("source_name"),
                        Instant.now(), rs.getTimestamp("updated_at").toInstant(), "APPROVED_DOCUMENT"),
                rs.getString("chunk_text")), vector, namedTerm, namedTerm, question, vector, properties.topK());
    }

    private String namedTerm(String question) {
        Matcher matcher = NAMED_TERM.matcher(question);
        String term = "";
        while (matcher.find()) {
            String candidate = matcher.group();
            if (!candidate.equals("CVP") && !candidate.equals("How") && !candidate.equals("What")) term = candidate;
        }
        return term;
    }

    @Transactional
    public int ingest(Path source) {
        try {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            String sourceName = source.getFileName().toString();
            String checksum = sha256(content);
            UUID documentId = UUID.nameUUIDFromBytes(sourceName.getBytes(StandardCharsets.UTF_8));
            String existing = jdbc.query("SELECT content_checksum FROM rag_document WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, documentId);
            if (checksum.equals(existing)) return jdbc.queryForObject("SELECT count(*) FROM rag_chunk WHERE document_id = ?", Integer.class, documentId);

            Instant now = Instant.now();
            jdbc.update("""
                    INSERT INTO rag_document (id, source_name, source_path, content_checksum, metadata, created_at, updated_at)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                    ON CONFLICT (id) DO UPDATE SET source_name = EXCLUDED.source_name, source_path = EXCLUDED.source_path,
                    content_checksum = EXCLUDED.content_checksum, metadata = EXCLUDED.metadata, updated_at = EXCLUDED.updated_at
                    """, documentId, sourceName, source.toAbsolutePath().toString(), checksum,
                    "{\"provenance\":\"approved-markdown\"}", Timestamp.from(now), Timestamp.from(now));
            jdbc.update("DELETE FROM rag_chunk WHERE document_id = ?", documentId);
            List<String> chunks = chunk(content);
            for (int index = 0; index < chunks.size(); index++) {
                String chunk = chunks.get(index);
                UUID chunkId = UUID.nameUUIDFromBytes((documentId + ":" + index).getBytes(StandardCharsets.UTF_8));
                jdbc.update("""
                        INSERT INTO rag_chunk (id, document_id, chunk_index, chunk_text, metadata, embedding, created_at, updated_at)
                        VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS float8[]), ?, ?)
                        """, chunkId, documentId, index, chunk, "{\"source\":\"" + sourceName + "\",\"chunkIndex\":" + index + "}",
                        postgresArray(embeddings.embed(chunk)), Timestamp.from(now), Timestamp.from(now));
            }
            return chunks.size();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not ingest RAG knowledge source " + source, exception);
        }
    }

    private List<String> chunk(String markdown) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("#") && current.length() > 0) {
                chunks.add(current.toString().trim()); current.setLength(0);
            }
            if (current.length() + line.length() > 1400 && current.length() > 0) {
                chunks.add(current.toString().trim()); current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (current.length() > 0) chunks.add(current.toString().trim());
        return chunks.stream().filter(value -> !value.isBlank()).toList();
    }

    private static String postgresArray(double[] values) {
        StringBuilder result = new StringBuilder("{");
        for (int i = 0; i < values.length; i++) { if (i > 0) result.append(','); result.append(values[i]); }
        return result.append('}').toString();
    }

    private static String sha256(String content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
