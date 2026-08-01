ALTER TABLE ai_jobs DROP CONSTRAINT chk_ai_jobs_feature;
ALTER TABLE ai_jobs ADD CONSTRAINT chk_ai_jobs_feature CHECK (feature IN (
    'ISSUE_DETECTION',
    'OPINION_SYNTHESIS',
    'DRAFT_GENERATION',
    'DRAFT_REVIEW',
    'AUTHOR_INTENT_EXTRACTION',
    'REVIEW_INTENT_COMPARISON'
));

ALTER TABLE ai_usage_logs DROP CONSTRAINT chk_ai_usage_logs_feature;
ALTER TABLE ai_usage_logs ADD CONSTRAINT chk_ai_usage_logs_feature CHECK (feature IN (
    'ISSUE_DETECTION',
    'OPINION_SYNTHESIS',
    'DRAFT_GENERATION',
    'DRAFT_REVIEW',
    'AUTHOR_INTENT_EXTRACTION',
    'REVIEW_INTENT_COMPARISON'
));

CREATE TABLE section_author_intents (
    id BIGSERIAL PRIMARY KEY,
    project_section_id BIGINT NOT NULL,
    content_version INTEGER NOT NULL,
    ai_suggested_intent VARCHAR(300),
    confirmed_intent VARCHAR(300),
    status VARCHAR(20) NOT NULL,
    source_ai_job_id BIGINT,
    confirmed_by_user_id BIGINT,
    confirmed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_section_author_intents_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_section_author_intents_draft
        FOREIGN KEY (project_section_id, content_version)
        REFERENCES section_drafts (project_section_id, version),
    CONSTRAINT fk_section_author_intents_source_job
        FOREIGN KEY (source_ai_job_id) REFERENCES ai_jobs (id),
    CONSTRAINT fk_section_author_intents_confirmer
        FOREIGN KEY (confirmed_by_user_id) REFERENCES users (id),
    CONSTRAINT uk_section_author_intents_version
        UNIQUE (project_section_id, content_version),
    CONSTRAINT chk_section_author_intents_version CHECK (content_version > 0),
    CONSTRAINT chk_section_author_intents_status
        CHECK (status IN ('SUGGESTED', 'CONFIRMED')),
    CONSTRAINT chk_section_author_intents_suggestion CHECK (
        ai_suggested_intent IS NULL OR (
            char_length(btrim(ai_suggested_intent)) > 0
            AND char_length(ai_suggested_intent) <= 300
            AND position(chr(10) in ai_suggested_intent) = 0
            AND position(chr(13) in ai_suggested_intent) = 0
        )
    ),
    CONSTRAINT chk_section_author_intents_confirmation CHECK (
        (status = 'SUGGESTED'
            AND ai_suggested_intent IS NOT NULL
            AND confirmed_intent IS NULL
            AND confirmed_by_user_id IS NULL
            AND confirmed_at IS NULL)
        OR
        (status = 'CONFIRMED'
            AND confirmed_intent IS NOT NULL
            AND char_length(btrim(confirmed_intent)) > 0
            AND char_length(confirmed_intent) <= 300
            AND position(chr(10) in confirmed_intent) = 0
            AND position(chr(13) in confirmed_intent) = 0
            AND confirmed_by_user_id IS NOT NULL
            AND confirmed_at IS NOT NULL)
    )
);

CREATE INDEX idx_section_author_intents_section_created
    ON section_author_intents (project_section_id, created_at);

ALTER TABLE review_links
    ADD COLUMN author_intent_snapshot VARCHAR(300),
    ADD COLUMN author_intent_id BIGINT;

ALTER TABLE review_links
    ADD CONSTRAINT fk_review_links_author_intent
        FOREIGN KEY (author_intent_id) REFERENCES section_author_intents (id),
    ADD CONSTRAINT chk_review_links_author_intent_snapshot CHECK (
        author_intent_snapshot IS NULL OR (
            char_length(btrim(author_intent_snapshot)) > 0
            AND char_length(author_intent_snapshot) <= 300
            AND position(chr(10) in author_intent_snapshot) = 0
            AND position(chr(13) in author_intent_snapshot) = 0
        )
    ),
    ADD CONSTRAINT chk_review_links_author_intent_pair CHECK (
        (author_intent_snapshot IS NULL AND author_intent_id IS NULL)
        OR (author_intent_snapshot IS NOT NULL AND author_intent_id IS NOT NULL)
    );

CREATE TABLE review_intent_comparisons (
    id BIGSERIAL PRIMARY KEY,
    review_submission_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    alignment VARCHAR(20),
    difference_summary VARCHAR(1000),
    evidence_excerpt VARCHAR(300),
    source_ai_job_id BIGINT,
    intent_snapshot_hash VARCHAR(64),
    reviewer_summary_hash VARCHAR(64),
    prompt_version VARCHAR(100),
    schema_version VARCHAR(100),
    model_id VARCHAR(100),
    failure_code VARCHAR(10),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_intent_comparisons_submission
        FOREIGN KEY (review_submission_id) REFERENCES review_submissions (id),
    CONSTRAINT fk_review_intent_comparisons_source_job
        FOREIGN KEY (source_ai_job_id) REFERENCES ai_jobs (id),
    CONSTRAINT uk_review_intent_comparisons_submission UNIQUE (review_submission_id),
    CONSTRAINT uk_review_intent_comparisons_job UNIQUE (source_ai_job_id),
    CONSTRAINT chk_review_intent_comparisons_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'NOT_AVAILABLE')),
    CONSTRAINT chk_review_intent_comparisons_alignment
        CHECK (alignment IS NULL OR alignment IN ('ALIGNED', 'PARTIAL', 'MISALIGNED')),
    CONSTRAINT chk_review_intent_comparisons_hashes CHECK (
        (intent_snapshot_hash IS NULL OR intent_snapshot_hash ~ '^[0-9a-f]{64}$')
        AND (reviewer_summary_hash IS NULL OR reviewer_summary_hash ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT chk_review_intent_comparisons_result CHECK (
        (status = 'SUCCEEDED'
            AND alignment IS NOT NULL
            AND difference_summary IS NOT NULL
            AND char_length(btrim(difference_summary)) > 0
            AND failure_code IS NULL)
        OR
        (status = 'FAILED'
            AND alignment IS NULL
            AND difference_summary IS NULL
            AND evidence_excerpt IS NULL
            AND failure_code IS NOT NULL)
        OR
        (status IN ('PENDING', 'NOT_AVAILABLE')
            AND alignment IS NULL
            AND difference_summary IS NULL
            AND evidence_excerpt IS NULL
            AND failure_code IS NULL)
    ),
    CONSTRAINT chk_review_intent_comparisons_job_metadata CHECK (
        (status = 'NOT_AVAILABLE'
            AND source_ai_job_id IS NULL
            AND intent_snapshot_hash IS NULL
            AND reviewer_summary_hash IS NULL
            AND prompt_version IS NULL
            AND schema_version IS NULL
            AND model_id IS NULL)
        OR
        (status <> 'NOT_AVAILABLE'
            AND intent_snapshot_hash IS NOT NULL
            AND reviewer_summary_hash IS NOT NULL
            AND prompt_version IS NOT NULL
            AND schema_version IS NOT NULL
            AND model_id IS NOT NULL)
    )
);

CREATE INDEX idx_review_intent_comparisons_status_created
    ON review_intent_comparisons (status, created_at);
