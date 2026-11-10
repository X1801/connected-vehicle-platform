ALTER TABLE conversation_turn ADD COLUMN turn_id uuid;
ALTER TABLE conversation_turn ADD COLUMN correlation_id varchar(120);
ALTER TABLE conversation_turn ADD COLUMN answer_classification varchar(32) NOT NULL DEFAULT 'ANSWER';
ALTER TABLE conversation_turn ADD CONSTRAINT conversation_turn_turn_id_uq UNIQUE (turn_id);
CREATE INDEX conversation_turn_conversation_turn_idx ON conversation_turn(conversation_id, turn_id);

CREATE TABLE conversation_turn_evidence (
    id uuid PRIMARY KEY,
    turn_id uuid NOT NULL REFERENCES conversation_turn(turn_id) ON DELETE CASCADE,
    evidence_id varchar(32), kind varchar(16) NOT NULL, citable boolean NOT NULL, cited boolean NOT NULL DEFAULT false,
    tool_execution_id uuid, tool_name varchar(120), execution_path varchar(32), status varchar(32), error_code varchar(80),
    facts text, source_id varchar(255), source_type varchar(80), source_service varchar(120), resource_id varchar(255),
    source_timestamp timestamptz, retrieved_at timestamptz, provenance varchar(120), excerpt text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT conversation_turn_evidence_turn_evidence_uq UNIQUE (turn_id, evidence_id)
);
CREATE INDEX conversation_turn_evidence_turn_idx ON conversation_turn_evidence(turn_id, cited);

CREATE TABLE conversation_turn_citation (
    turn_id uuid NOT NULL REFERENCES conversation_turn(turn_id) ON DELETE CASCADE,
    evidence_id varchar(32) NOT NULL,
    PRIMARY KEY (turn_id, evidence_id),
    CONSTRAINT conversation_turn_citation_evidence_fk
        FOREIGN KEY (turn_id, evidence_id)
        REFERENCES conversation_turn_evidence(turn_id, evidence_id) ON DELETE CASCADE
);
