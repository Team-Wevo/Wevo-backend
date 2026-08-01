ALTER TABLE ai_jobs DROP CONSTRAINT chk_ai_jobs_feature;
ALTER TABLE ai_jobs ADD CONSTRAINT chk_ai_jobs_feature CHECK (feature IN (
    'ISSUE_DETECTION',
    'OPINION_SYNTHESIS',
    'DRAFT_GENERATION',
    'DRAFT_REVIEW',
    'AUTHOR_INTENT_EXTRACTION',
    'REVIEW_INTENT_COMPARISON',
    'OPINION_CLUSTERING'
));

ALTER TABLE ai_usage_logs DROP CONSTRAINT chk_ai_usage_logs_feature;
ALTER TABLE ai_usage_logs ADD CONSTRAINT chk_ai_usage_logs_feature CHECK (feature IN (
    'ISSUE_DETECTION',
    'OPINION_SYNTHESIS',
    'DRAFT_GENERATION',
    'DRAFT_REVIEW',
    'AUTHOR_INTENT_EXTRACTION',
    'REVIEW_INTENT_COMPARISON',
    'OPINION_CLUSTERING'
));

CREATE TABLE opinion_cluster_sets (
    id BIGSERIAL PRIMARY KEY,
    source_ai_job_id BIGINT NOT NULL,
    project_section_id BIGINT NOT NULL,
    opinion_gate_generation BIGINT NOT NULL,
    input_snapshot_hash VARCHAR(64) NOT NULL,
    source_version VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    schema_version VARCHAR(100) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    total_opinion_count INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_opinion_cluster_sets_job FOREIGN KEY (source_ai_job_id) REFERENCES ai_jobs (id),
    CONSTRAINT fk_opinion_cluster_sets_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT uk_opinion_cluster_sets_source_job UNIQUE (source_ai_job_id),
    CONSTRAINT chk_opinion_cluster_sets_gate CHECK (opinion_gate_generation >= 0),
    CONSTRAINT chk_opinion_cluster_sets_hash
        CHECK (input_snapshot_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_opinion_cluster_sets_count CHECK (total_opinion_count > 0)
);

CREATE INDEX idx_opinion_cluster_sets_section_created
    ON opinion_cluster_sets (project_section_id, created_at DESC);

CREATE TABLE opinion_clusters (
    id BIGSERIAL PRIMARY KEY,
    cluster_set_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    title VARCHAR(60) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_opinion_clusters_set
        FOREIGN KEY (cluster_set_id) REFERENCES opinion_cluster_sets (id),
    CONSTRAINT uk_opinion_clusters_set_order UNIQUE (cluster_set_id, sort_order),
    CONSTRAINT uk_opinion_clusters_id_set UNIQUE (id, cluster_set_id),
    CONSTRAINT chk_opinion_clusters_order CHECK (sort_order > 0),
    CONSTRAINT chk_opinion_clusters_title
        CHECK (char_length(btrim(title)) > 0 AND char_length(title) <= 60),
    CONSTRAINT chk_opinion_clusters_summary
        CHECK (char_length(btrim(summary)) > 0 AND char_length(summary) <= 500)
);

CREATE TABLE opinion_cluster_members (
    id BIGSERIAL PRIMARY KEY,
    cluster_set_id BIGINT NOT NULL,
    cluster_id BIGINT NOT NULL,
    opinion_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_opinion_cluster_members_cluster
        FOREIGN KEY (cluster_id, cluster_set_id) REFERENCES opinion_clusters (id, cluster_set_id),
    CONSTRAINT fk_opinion_cluster_members_opinion FOREIGN KEY (opinion_id) REFERENCES opinions (id),
    CONSTRAINT uk_opinion_cluster_members_set_opinion UNIQUE (cluster_set_id, opinion_id),
    CONSTRAINT uk_opinion_cluster_members_cluster_order UNIQUE (cluster_id, sort_order),
    CONSTRAINT chk_opinion_cluster_members_order CHECK (sort_order > 0)
);

CREATE TABLE opinion_cluster_inputs (
    id BIGSERIAL PRIMARY KEY,
    cluster_set_id BIGINT NOT NULL,
    opinion_id BIGINT NOT NULL,
    submitted_content_hash VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_opinion_cluster_inputs_set
        FOREIGN KEY (cluster_set_id) REFERENCES opinion_cluster_sets (id),
    CONSTRAINT fk_opinion_cluster_inputs_opinion FOREIGN KEY (opinion_id) REFERENCES opinions (id),
    CONSTRAINT uk_opinion_cluster_inputs_set_opinion UNIQUE (cluster_set_id, opinion_id),
    CONSTRAINT uk_opinion_cluster_inputs_set_order UNIQUE (cluster_set_id, sort_order),
    CONSTRAINT chk_opinion_cluster_inputs_hash
        CHECK (submitted_content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_opinion_cluster_inputs_order CHECK (sort_order > 0)
);
