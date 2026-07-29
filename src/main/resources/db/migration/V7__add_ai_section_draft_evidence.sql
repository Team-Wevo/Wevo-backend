ALTER TABLE section_drafts
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'USER_EDITED';

ALTER TABLE section_drafts
    ADD CONSTRAINT chk_section_drafts_source
        CHECK (source IN ('AI_GENERATED', 'USER_EDITED', 'AI_REWRITE_APPLIED'));

CREATE TABLE section_draft_evidence (
    id BIGSERIAL PRIMARY KEY,
    section_draft_id BIGINT NOT NULL,
    synthesis_set_id BIGINT NOT NULL,
    opinion_gate_generation BIGINT NOT NULL,
    generation_request_id UUID NOT NULL,
    input_snapshot_hash VARCHAR(64) NOT NULL,
    source_version VARCHAR(100) NOT NULL,
    consensus_summary TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_section_draft_evidence_draft
        FOREIGN KEY (section_draft_id) REFERENCES section_drafts (id),
    CONSTRAINT fk_section_draft_evidence_synthesis
        FOREIGN KEY (synthesis_set_id) REFERENCES synthesis_sets (id),
    CONSTRAINT fk_section_draft_evidence_job
        FOREIGN KEY (generation_request_id) REFERENCES ai_jobs (request_id),
    CONSTRAINT uk_section_draft_evidence_draft UNIQUE (section_draft_id),
    CONSTRAINT uk_section_draft_evidence_request UNIQUE (generation_request_id),
    CONSTRAINT chk_section_draft_evidence_generation CHECK (opinion_gate_generation >= 0),
    CONSTRAINT chk_section_draft_evidence_hash
        CHECK (input_snapshot_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_section_draft_evidence_source_version
        CHECK (char_length(btrim(source_version)) > 0),
    CONSTRAINT chk_section_draft_evidence_consensus
        CHECK (char_length(btrim(consensus_summary)) > 0)
);

CREATE TABLE section_draft_evidence_opinions (
    id BIGSERIAL PRIMARY KEY,
    draft_evidence_id BIGINT NOT NULL,
    opinion_id BIGINT NOT NULL,
    author_name_snapshot VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_draft_evidence_opinions_root
        FOREIGN KEY (draft_evidence_id) REFERENCES section_draft_evidence (id),
    CONSTRAINT fk_draft_evidence_opinions_opinion
        FOREIGN KEY (opinion_id) REFERENCES opinions (id),
    CONSTRAINT uk_draft_evidence_opinion UNIQUE (draft_evidence_id, opinion_id),
    CONSTRAINT uk_draft_evidence_opinion_order UNIQUE (draft_evidence_id, sort_order),
    CONSTRAINT chk_draft_evidence_opinion_author
        CHECK (char_length(btrim(author_name_snapshot)) > 0),
    CONSTRAINT chk_draft_evidence_opinion_content
        CHECK (char_length(btrim(content)) > 0),
    CONSTRAINT chk_draft_evidence_opinion_order CHECK (sort_order > 0)
);

CREATE TABLE section_draft_evidence_decisions (
    id BIGSERIAL PRIMARY KEY,
    draft_evidence_id BIGINT NOT NULL,
    issue_id BIGINT NOT NULL,
    decision_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    decision TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_draft_evidence_decisions_root
        FOREIGN KEY (draft_evidence_id) REFERENCES section_draft_evidence (id),
    CONSTRAINT fk_draft_evidence_decisions_issue
        FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT fk_draft_evidence_decisions_decision
        FOREIGN KEY (decision_id) REFERENCES issue_decisions (id),
    CONSTRAINT uk_draft_evidence_decision_issue UNIQUE (draft_evidence_id, issue_id),
    CONSTRAINT uk_draft_evidence_decision_order UNIQUE (draft_evidence_id, sort_order),
    CONSTRAINT chk_draft_evidence_decision_question
        CHECK (char_length(btrim(question)) > 0),
    CONSTRAINT chk_draft_evidence_decision_content
        CHECK (char_length(btrim(decision)) > 0),
    CONSTRAINT chk_draft_evidence_decision_order CHECK (sort_order > 0)
);

CREATE TABLE section_draft_evidence_gap_answers (
    id BIGSERIAL PRIMARY KEY,
    draft_evidence_id BIGINT NOT NULL,
    source_issue_id BIGINT NOT NULL,
    answer_id BIGINT NOT NULL,
    author_name_snapshot VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    answered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    inherited BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_draft_evidence_gap_root
        FOREIGN KEY (draft_evidence_id) REFERENCES section_draft_evidence (id),
    CONSTRAINT fk_draft_evidence_gap_answer
        FOREIGN KEY (answer_id, source_issue_id) REFERENCES issue_answers (id, issue_id),
    CONSTRAINT uk_draft_evidence_gap_answer UNIQUE (draft_evidence_id, answer_id),
    CONSTRAINT uk_draft_evidence_gap_order UNIQUE (draft_evidence_id, sort_order),
    CONSTRAINT chk_draft_evidence_gap_author
        CHECK (char_length(btrim(author_name_snapshot)) > 0),
    CONSTRAINT chk_draft_evidence_gap_content
        CHECK (char_length(btrim(content)) > 0),
    CONSTRAINT chk_draft_evidence_gap_order CHECK (sort_order > 0)
);

CREATE TABLE section_draft_evidence_prerequisites (
    id BIGSERIAL PRIMARY KEY,
    draft_evidence_id BIGINT NOT NULL,
    source_section_id BIGINT NOT NULL,
    content_version INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_draft_evidence_prerequisites_root
        FOREIGN KEY (draft_evidence_id) REFERENCES section_draft_evidence (id),
    CONSTRAINT fk_draft_evidence_prerequisites_section
        FOREIGN KEY (source_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_draft_evidence_prerequisites_draft
        FOREIGN KEY (source_section_id, content_version)
        REFERENCES section_drafts (project_section_id, version),
    CONSTRAINT uk_draft_evidence_prerequisite
        UNIQUE (draft_evidence_id, source_section_id),
    CONSTRAINT uk_draft_evidence_prerequisite_order
        UNIQUE (draft_evidence_id, sort_order),
    CONSTRAINT chk_draft_evidence_prerequisite_version CHECK (content_version > 0),
    CONSTRAINT chk_draft_evidence_prerequisite_order CHECK (sort_order > 0)
);
