-- V14 follows the reviewer-comment V13 merged from dev.
ALTER TABLE ai_jobs DROP CONSTRAINT chk_ai_jobs_feature;
ALTER TABLE ai_jobs ADD CONSTRAINT chk_ai_jobs_feature CHECK (feature IN (
    'ISSUE_DETECTION', 'OPINION_SYNTHESIS', 'DRAFT_GENERATION', 'DRAFT_REVIEW',
    'AUTHOR_INTENT_EXTRACTION', 'REVIEW_INTENT_COMPARISON', 'OPINION_CLUSTERING',
    'PROJECT_FLOW_REVIEW'
));

ALTER TABLE ai_usage_logs DROP CONSTRAINT chk_ai_usage_logs_feature;
ALTER TABLE ai_usage_logs ADD CONSTRAINT chk_ai_usage_logs_feature CHECK (feature IN (
    'ISSUE_DETECTION', 'OPINION_SYNTHESIS', 'DRAFT_GENERATION', 'DRAFT_REVIEW',
    'AUTHOR_INTENT_EXTRACTION', 'REVIEW_INTENT_COMPARISON', 'OPINION_CLUSTERING',
    'PROJECT_FLOW_REVIEW'
));

CREATE TABLE project_flow_checks (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_ai_job_id BIGINT NOT NULL,
    input_snapshot_hash VARCHAR(64) NOT NULL,
    source_version VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    schema_version VARCHAR(100) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    section_count INTEGER NOT NULL,
    finding_count INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_flow_checks_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_flow_checks_job FOREIGN KEY (source_ai_job_id) REFERENCES ai_jobs (id),
    CONSTRAINT uk_project_flow_checks_source_job UNIQUE (source_ai_job_id),
    CONSTRAINT chk_project_flow_checks_hash CHECK (input_snapshot_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_project_flow_checks_counts CHECK (section_count > 0 AND finding_count >= 0)
);
CREATE INDEX idx_project_flow_checks_project_created
    ON project_flow_checks (project_id, created_at DESC);

CREATE TABLE project_flow_check_inputs (
    id BIGSERIAL PRIMARY KEY,
    flow_check_id BIGINT NOT NULL,
    project_section_id BIGINT NOT NULL,
    confirmed_version INTEGER NOT NULL,
    section_key VARCHAR(100) NOT NULL,
    section_title VARCHAR(200) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_flow_inputs_check FOREIGN KEY (flow_check_id) REFERENCES project_flow_checks (id),
    CONSTRAINT fk_project_flow_inputs_section FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT uk_project_flow_inputs_check_section UNIQUE (flow_check_id, project_section_id),
    CONSTRAINT uk_project_flow_inputs_check_order UNIQUE (flow_check_id, sort_order),
    CONSTRAINT chk_project_flow_inputs_version_order CHECK (confirmed_version > 0 AND sort_order > 0),
    CONSTRAINT chk_project_flow_inputs_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_project_flow_inputs_text CHECK (
        char_length(btrim(section_key)) > 0 AND char_length(btrim(section_title)) > 0)
);

CREATE TABLE project_flow_check_findings (
    id BIGSERIAL PRIMARY KEY,
    flow_check_id BIGINT NOT NULL,
    type VARCHAR(60) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    suggestion VARCHAR(1000) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_flow_findings_check FOREIGN KEY (flow_check_id) REFERENCES project_flow_checks (id),
    CONSTRAINT uk_project_flow_findings_check_order UNIQUE (flow_check_id, sort_order),
    CONSTRAINT uk_project_flow_findings_id_check UNIQUE (id, flow_check_id),
    CONSTRAINT chk_project_flow_findings_type CHECK (type IN (
        'CLAIM_OR_NUMBER_CONTRADICTION', 'LOGICAL_CONNECTION_MISSING', 'REDUNDANT_CONTENT',
        'TERMINOLOGY_AUDIENCE_TONE_MISMATCH', 'DEPENDENCY_NOT_REFLECTED')),
    CONSTRAINT chk_project_flow_findings_text CHECK (
        char_length(btrim(description)) > 0 AND char_length(btrim(suggestion)) > 0 AND sort_order > 0)
);

CREATE TABLE project_flow_finding_sections (
    id BIGSERIAL PRIMARY KEY,
    finding_id BIGINT NOT NULL,
    project_section_id BIGINT NOT NULL,
    confirmed_version INTEGER NOT NULL,
    target_excerpt VARCHAR(500) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_flow_finding_sections_finding FOREIGN KEY (finding_id) REFERENCES project_flow_check_findings (id),
    CONSTRAINT fk_project_flow_finding_sections_section FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT uk_project_flow_finding_section UNIQUE (finding_id, project_section_id),
    CONSTRAINT uk_project_flow_finding_order UNIQUE (finding_id, sort_order),
    CONSTRAINT chk_project_flow_finding_sections_values CHECK (
        confirmed_version > 0 AND sort_order > 0 AND char_length(btrim(target_excerpt)) > 0)
);
