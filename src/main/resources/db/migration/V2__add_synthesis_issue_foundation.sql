-- Opinion synthesis result history and issue follow-up foundation.
-- V1 is frozen; every schema change for the synthesis/issue milestone lives here.

CREATE TABLE synthesis_sets (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL,
    project_section_id BIGINT NOT NULL,
    opinion_gate_generation BIGINT NOT NULL,
    consensus_summary TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_synthesis_sets_job
        FOREIGN KEY (request_id) REFERENCES ai_jobs (request_id),
    CONSTRAINT fk_synthesis_sets_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT uk_synthesis_sets_request UNIQUE (request_id),
    CONSTRAINT chk_synthesis_sets_gate_generation
        CHECK (opinion_gate_generation >= 0),
    CONSTRAINT chk_synthesis_sets_consensus_summary
        CHECK (char_length(btrim(consensus_summary)) > 0)
);

CREATE INDEX idx_synthesis_sets_section_created
    ON synthesis_sets (project_section_id, created_at DESC);

CREATE TABLE issues (
    id BIGSERIAL PRIMARY KEY,
    synthesis_set_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    question TEXT,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issues_synthesis_set
        FOREIGN KEY (synthesis_set_id) REFERENCES synthesis_sets (id),
    CONSTRAINT uk_issues_set_order UNIQUE (synthesis_set_id, sort_order),
    CONSTRAINT chk_issues_type CHECK (type IN ('CONFLICT', 'GAP')),
    CONSTRAINT chk_issues_status CHECK (status IN ('PENDING', 'RESOLVED')),
    CONSTRAINT chk_issues_description
        CHECK (char_length(btrim(description)) > 0),
    CONSTRAINT chk_issues_question_by_type CHECK (
        (type = 'CONFLICT' AND question IS NOT NULL AND char_length(btrim(question)) > 0)
        OR (type = 'GAP' AND question IS NULL)
    ),
    CONSTRAINT chk_issues_sort_order CHECK (sort_order > 0)
);

CREATE INDEX idx_issues_synthesis_set_type
    ON issues (synthesis_set_id, type);

CREATE TABLE issue_options (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    option_text TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issue_options_issue FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT uk_issue_options_issue_order UNIQUE (issue_id, sort_order),
    CONSTRAINT uk_issue_options_issue_text UNIQUE (issue_id, option_text),
    CONSTRAINT uk_issue_options_id_issue UNIQUE (id, issue_id),
    CONSTRAINT chk_issue_options_text CHECK (char_length(btrim(option_text)) > 0),
    CONSTRAINT chk_issue_options_sort_order CHECK (sort_order > 0)
);

CREATE TABLE issue_related_opinions (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    opinion_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    author_name_snapshot VARCHAR(100) NOT NULL,
    excerpt TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issue_related_opinions_issue FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT fk_issue_related_opinions_opinion FOREIGN KEY (opinion_id) REFERENCES opinions (id),
    CONSTRAINT fk_issue_related_opinions_author FOREIGN KEY (author_user_id) REFERENCES users (id),
    CONSTRAINT uk_issue_related_opinions_issue_opinion UNIQUE (issue_id, opinion_id),
    CONSTRAINT uk_issue_related_opinions_issue_order UNIQUE (issue_id, sort_order),
    CONSTRAINT chk_issue_related_opinions_author_name
        CHECK (char_length(btrim(author_name_snapshot)) > 0),
    CONSTRAINT chk_issue_related_opinions_excerpt
        CHECK (char_length(btrim(excerpt)) > 0),
    CONSTRAINT chk_issue_related_opinions_sort_order CHECK (sort_order > 0)
);

CREATE INDEX idx_issue_related_opinions_author
    ON issue_related_opinions (issue_id, author_user_id);

CREATE TABLE issue_decisions (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    decided_by_user_id BIGINT NOT NULL,
    selected_option_id BIGINT,
    custom_input VARCHAR(200),
    decided_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issue_decisions_issue FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT fk_issue_decisions_decided_by FOREIGN KEY (decided_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_issue_decisions_selected_option
        FOREIGN KEY (selected_option_id, issue_id) REFERENCES issue_options (id, issue_id),
    CONSTRAINT uk_issue_decisions_issue UNIQUE (issue_id),
    CONSTRAINT chk_issue_decisions_choice CHECK (
        (selected_option_id IS NOT NULL AND custom_input IS NULL)
        OR (selected_option_id IS NULL AND custom_input IS NOT NULL
            AND char_length(btrim(custom_input)) > 0)
    )
);

CREATE TABLE evidence_requests (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_requests_issue FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT fk_evidence_requests_requested_by
        FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_evidence_requests_target FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT uk_evidence_requests_issue UNIQUE (issue_id),
    CONSTRAINT uk_evidence_requests_id_issue UNIQUE (id, issue_id)
);

CREATE TABLE issue_answers (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    evidence_request_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    author_name_snapshot VARCHAR(100) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    answered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issue_answers_issue FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT fk_issue_answers_evidence_request
        FOREIGN KEY (evidence_request_id, issue_id) REFERENCES evidence_requests (id, issue_id),
    CONSTRAINT fk_issue_answers_author FOREIGN KEY (author_user_id) REFERENCES users (id),
    CONSTRAINT uk_issue_answers_issue UNIQUE (issue_id),
    CONSTRAINT uk_issue_answers_evidence_request UNIQUE (evidence_request_id),
    CONSTRAINT uk_issue_answers_id_issue UNIQUE (id, issue_id),
    CONSTRAINT chk_issue_answers_author_name
        CHECK (char_length(btrim(author_name_snapshot)) > 0),
    CONSTRAINT chk_issue_answers_content CHECK (char_length(btrim(content)) > 0)
);

CREATE TABLE synthesis_inherited_gap_answers (
    id BIGSERIAL PRIMARY KEY,
    synthesis_set_id BIGINT NOT NULL,
    source_issue_id BIGINT NOT NULL,
    source_answer_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_synthesis_inherited_answers_set
        FOREIGN KEY (synthesis_set_id) REFERENCES synthesis_sets (id),
    CONSTRAINT fk_synthesis_inherited_answers_source
        FOREIGN KEY (source_answer_id, source_issue_id) REFERENCES issue_answers (id, issue_id),
    CONSTRAINT uk_synthesis_inherited_answers_set_answer
        UNIQUE (synthesis_set_id, source_answer_id)
);

CREATE INDEX idx_synthesis_inherited_answers_source_issue
    ON synthesis_inherited_gap_answers (source_issue_id);

-- A constant default is metadata-only on supported PostgreSQL versions. Keep this ALTER last so
-- its short ACCESS EXCLUSIVE phase is released as soon as V2 commits. Existing rows receive 0.
ALTER TABLE project_sections
    ADD COLUMN opinion_gate_generation BIGINT NOT NULL DEFAULT 0;

-- NOT VALID avoids scanning existing project_sections while holding V2's ACCESS EXCLUSIVE lock.
-- New and updated rows are still checked. V3 validates existing rows in a separate transaction.
ALTER TABLE project_sections
    ADD CONSTRAINT chk_project_sections_opinion_gate_generation
        CHECK (opinion_gate_generation >= 0) NOT VALID;
