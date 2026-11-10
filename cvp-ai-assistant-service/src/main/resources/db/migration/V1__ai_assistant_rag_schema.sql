CREATE TABLE conversation (
    id VARCHAR(80) PRIMARY KEY,
    principal VARCHAR(255) NOT NULL,
    vehicle_id VARCHAR(80) NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE conversation_turn (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(80) NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    user_message TEXT NOT NULL,
    assistant_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rag_document (
    id UUID PRIMARY KEY,
    source_name VARCHAR(255) NOT NULL UNIQUE,
    source_path TEXT NOT NULL,
    content_checksum CHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE rag_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES rag_document(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding DOUBLE PRECISION[] NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT rag_chunk_document_index_unique UNIQUE (document_id, chunk_index),
    CONSTRAINT rag_chunk_embedding_not_empty CHECK (cardinality(embedding) > 0)
);

CREATE INDEX rag_chunk_document_id_idx ON rag_chunk(document_id);
