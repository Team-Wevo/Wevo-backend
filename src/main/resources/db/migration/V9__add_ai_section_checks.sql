CREATE TABLE ai_section_checks (
    id BIGSERIAL PRIMARY KEY,
    project_section_id BIGINT NOT NULL,
    source_job_id BIGINT NOT NULL,
    checked_draft_id BIGINT NOT NULL,
    checked_content_version INTEGER NOT NULL,
    input_snapshot_hash VARCHAR(64) NOT NULL,
    source_version VARCHAR(100) NOT NULL,
    dependency_version_hash VARCHAR(16) NOT NULL,
    rewrite_content TEXT NOT NULL,
    changed_count INTEGER NOT NULL,
    rewrite_applied BOOLEAN NOT NULL DEFAULT FALSE,
    applied_content_version INTEGER,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_section_checks_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_ai_section_checks_job
        FOREIGN KEY (source_job_id) REFERENCES ai_jobs (id),
    CONSTRAINT fk_ai_section_checks_draft
        FOREIGN KEY (checked_draft_id) REFERENCES section_drafts (id),
    CONSTRAINT uk_ai_section_checks_job UNIQUE (source_job_id),
    CONSTRAINT chk_ai_section_checks_version CHECK (checked_content_version > 0),
    CONSTRAINT chk_ai_section_checks_hash
        CHECK (input_snapshot_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_ai_section_checks_source_version
        CHECK (char_length(btrim(source_version)) > 0),
    CONSTRAINT chk_ai_section_checks_dependency_hash
        CHECK (dependency_version_hash ~ '^[0-9a-f]{16}$'),
    CONSTRAINT chk_ai_section_checks_rewrite
        CHECK (char_length(btrim(rewrite_content)) > 0
            AND char_length(rewrite_content) <= 10000),
    CONSTRAINT chk_ai_section_checks_changed_count CHECK (changed_count >= 0),
    CONSTRAINT chk_ai_section_checks_applied
        CHECK ((rewrite_applied = FALSE AND applied_content_version IS NULL)
            OR (rewrite_applied = TRUE AND applied_content_version > 0))
);

CREATE INDEX idx_ai_section_checks_section_created
    ON ai_section_checks (project_section_id, created_at);

CREATE TABLE ai_section_check_findings (
    id BIGSERIAL PRIMARY KEY,
    ai_section_check_id BIGINT NOT NULL,
    finding_type VARCHAR(40) NOT NULL,
    target_excerpt TEXT NOT NULL,
    comment_text TEXT NOT NULL,
    suggestion TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_section_check_findings_check
        FOREIGN KEY (ai_section_check_id) REFERENCES ai_section_checks (id),
    CONSTRAINT uk_ai_section_check_findings_order
        UNIQUE (ai_section_check_id, sort_order),
    CONSTRAINT chk_ai_section_check_findings_type
        CHECK (finding_type IN (
            'UNCLEAR_SENTENCE',
            'HIDDEN_ASSUMPTION',
            'PREREQUISITE_CONFLICT',
            'READER_QUESTION'
        )),
    CONSTRAINT chk_ai_section_check_findings_excerpt
        CHECK (char_length(btrim(target_excerpt)) > 0),
    CONSTRAINT chk_ai_section_check_findings_comment
        CHECK (char_length(btrim(comment_text)) > 0),
    CONSTRAINT chk_ai_section_check_findings_suggestion
        CHECK (char_length(btrim(suggestion)) > 0),
    CONSTRAINT chk_ai_section_check_findings_order CHECK (sort_order > 0)
);

CREATE TABLE ai_section_check_prerequisites (
    id BIGSERIAL PRIMARY KEY,
    ai_section_check_id BIGINT NOT NULL,
    source_section_id BIGINT NOT NULL,
    content_version INTEGER NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_section_check_prerequisites_check
        FOREIGN KEY (ai_section_check_id) REFERENCES ai_section_checks (id),
    CONSTRAINT fk_ai_section_check_prerequisites_section
        FOREIGN KEY (source_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_ai_section_check_prerequisites_draft
        FOREIGN KEY (source_section_id, content_version)
        REFERENCES section_drafts (project_section_id, version),
    CONSTRAINT uk_ai_section_check_prerequisite
        UNIQUE (ai_section_check_id, source_section_id),
    CONSTRAINT uk_ai_section_check_prerequisite_order
        UNIQUE (ai_section_check_id, sort_order),
    CONSTRAINT chk_ai_section_check_prerequisite_version CHECK (content_version > 0),
    CONSTRAINT chk_ai_section_check_prerequisite_order CHECK (sort_order > 0)
);
