CREATE TABLE synthesis_consensus_evidence (
    id BIGSERIAL PRIMARY KEY,
    synthesis_set_id BIGINT NOT NULL,
    opinion_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    author_name_snapshot VARCHAR(100) NOT NULL,
    excerpt TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_synthesis_consensus_evidence_set
        FOREIGN KEY (synthesis_set_id) REFERENCES synthesis_sets (id),
    CONSTRAINT fk_synthesis_consensus_evidence_opinion
        FOREIGN KEY (opinion_id) REFERENCES opinions (id),
    CONSTRAINT fk_synthesis_consensus_evidence_author
        FOREIGN KEY (author_user_id) REFERENCES users (id),
    CONSTRAINT uk_synthesis_consensus_evidence_set_opinion
        UNIQUE (synthesis_set_id, opinion_id),
    CONSTRAINT uk_synthesis_consensus_evidence_set_order
        UNIQUE (synthesis_set_id, sort_order),
    CONSTRAINT chk_synthesis_consensus_evidence_author_name
        CHECK (char_length(btrim(author_name_snapshot)) > 0),
    CONSTRAINT chk_synthesis_consensus_evidence_excerpt
        CHECK (char_length(btrim(excerpt)) > 0),
    CONSTRAINT chk_synthesis_consensus_evidence_sort_order
        CHECK (sort_order > 0)
);

CREATE INDEX idx_synthesis_consensus_evidence_opinion
    ON synthesis_consensus_evidence (opinion_id);
