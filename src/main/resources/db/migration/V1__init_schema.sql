-- Wevo MVP initial PostgreSQL schema.
--
-- This migration is the database source of truth after issue #88. Hibernate only
-- validates it (`ddl-auto=validate`); every later schema change must use a new Vn.
-- LocalDateTime values follow the project-wide KST contract and are stored as
-- TIMESTAMP WITHOUT TIME ZONE.

-- -----------------------------------------------------------------------------
-- Identity and project roots
-- -----------------------------------------------------------------------------

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    profile_image_url VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'WITHDRAWN'))
);

CREATE TABLE auth_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_auth_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT chk_auth_accounts_provider CHECK (provider IN ('GOOGLE', 'KAKAO'))
);

CREATE TABLE section_templates (
    id BIGSERIAL PRIMARY KEY,
    result_type VARCHAR(30) NOT NULL,
    section_key VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    guide_text TEXT,
    order_no INTEGER NOT NULL,
    is_required BOOLEAN NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_section_templates_result_key UNIQUE (result_type, section_key),
    CONSTRAINT uk_section_templates_result_order UNIQUE (result_type, order_no),
    CONSTRAINT chk_section_templates_result_type
        CHECK (result_type IN ('PROPOSAL', 'PRESENTATION')),
    CONSTRAINT chk_section_templates_order CHECK (order_no > 0)
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    idea_text TEXT,
    result_type VARCHAR(30) NOT NULL,
    audience VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT chk_projects_result_type CHECK (result_type IN ('PROPOSAL', 'PRESENTATION')),
    CONSTRAINT chk_projects_status CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED'))
);

CREATE TABLE project_members (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT chk_project_members_role CHECK (role IN ('OWNER', 'MEMBER'))
);

CREATE INDEX idx_project_members_user ON project_members (user_id);
CREATE INDEX idx_project_members_project_role ON project_members (project_id, role);

CREATE TABLE invite_links (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    created_by_user_id BIGINT,
    token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invite_links_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_invite_links_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT uk_invite_links_token UNIQUE (token)
);

CREATE INDEX idx_invite_links_project_active ON invite_links (project_id, is_active);

CREATE TABLE template_dependencies (
    id BIGSERIAL PRIMARY KEY,
    from_template_id BIGINT NOT NULL,
    to_template_id BIGINT NOT NULL,
    dependency_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_template_dependencies_from
        FOREIGN KEY (from_template_id) REFERENCES section_templates (id),
    CONSTRAINT fk_template_dependencies_to
        FOREIGN KEY (to_template_id) REFERENCES section_templates (id),
    CONSTRAINT uk_template_dependencies_pair_type
        UNIQUE (from_template_id, to_template_id, dependency_type),
    CONSTRAINT chk_template_dependencies_type
        CHECK (dependency_type IN ('REQUIRES', 'BLOCKS', 'RECOMMENDS')),
    CONSTRAINT chk_template_dependencies_distinct
        CHECK (from_template_id <> to_template_id)
);

CREATE INDEX idx_template_dependencies_to ON template_dependencies (to_template_id);

-- -----------------------------------------------------------------------------
-- Section collaboration, opinions, and review
-- -----------------------------------------------------------------------------

CREATE TABLE project_sections (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    template_id BIGINT,
    title VARCHAR(200) NOT NULL,
    section_order INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    confirmed_version INTEGER NOT NULL DEFAULT 0,
    drift_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ai_check_status VARCHAR(30),
    synthesis_stale BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_sections_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_sections_template FOREIGN KEY (template_id) REFERENCES section_templates (id),
    CONSTRAINT uk_project_sections_project_order UNIQUE (project_id, section_order),
    CONSTRAINT uk_project_sections_project_template UNIQUE (project_id, template_id),
    CONSTRAINT chk_project_sections_status
        CHECK (status IN ('COLLECTING', 'SYNTHESIZING', 'DRAFTING', 'REVIEWING', 'CONFIRMED')),
    CONSTRAINT chk_project_sections_confirmed_version CHECK (confirmed_version >= 0),
    CONSTRAINT chk_project_sections_drift_status
        CHECK (drift_status IN ('NONE', 'REVIEW_REQUIRED')),
    CONSTRAINT chk_project_sections_ai_check_status
        CHECK (ai_check_status IS NULL OR ai_check_status IN ('CURRENT', 'OUTDATED')),
    CONSTRAINT chk_project_sections_order CHECK (section_order > 0)
);

CREATE INDEX idx_project_sections_project_status ON project_sections (project_id, status);

CREATE TABLE section_labels (
    id BIGSERIAL PRIMARY KEY,
    project_section_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_section_labels_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT uk_section_labels_section_order UNIQUE (project_section_id, sort_order),
    CONSTRAINT chk_section_labels_sort_order CHECK (sort_order > 0)
);

CREATE TABLE section_status_histories (
    id BIGSERIAL PRIMARY KEY,
    project_section_id BIGINT NOT NULL,
    actor_user_id BIGINT,
    event_type VARCHAR(30) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    version INTEGER,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_section_status_histories_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_section_status_histories_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_section_status_histories_from
        CHECK (from_status IS NULL OR from_status IN
            ('COLLECTING', 'SYNTHESIZING', 'DRAFTING', 'REVIEWING', 'CONFIRMED')),
    CONSTRAINT chk_section_status_histories_to
        CHECK (to_status IN ('COLLECTING', 'SYNTHESIZING', 'DRAFTING', 'REVIEWING', 'CONFIRMED')),
    CONSTRAINT chk_section_status_histories_version CHECK (version IS NULL OR version >= 0)
);

CREATE INDEX idx_section_status_histories_section_created
    ON section_status_histories (project_section_id, created_at);

CREATE TABLE section_drafts (
    id BIGSERIAL PRIMARY KEY,
    project_section_id BIGINT NOT NULL,
    content TEXT,
    version INTEGER NOT NULL,
    last_editor_user_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_section_drafts_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_section_drafts_last_editor
        FOREIGN KEY (last_editor_user_id) REFERENCES users (id),
    CONSTRAINT uk_section_drafts_section_version UNIQUE (project_section_id, version),
    CONSTRAINT chk_section_drafts_version CHECK (version > 0)
);

CREATE TABLE draft_leases (
    id BIGSERIAL PRIMARY KEY,
    project_section_id BIGINT NOT NULL,
    holder_user_id BIGINT NOT NULL,
    lease_until TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_draft_leases_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_draft_leases_holder FOREIGN KEY (holder_user_id) REFERENCES users (id),
    CONSTRAINT uk_draft_leases_section UNIQUE (project_section_id)
);

-- The unique section index serves acquire/status/renew row lookup and locking.
-- This additional index supports expiration scans without weakening that invariant.
CREATE INDEX idx_draft_leases_lease_until ON draft_leases (lease_until);

CREATE TABLE opinions (
    id BIGSERIAL PRIMARY KEY,
    project_section_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    submitted_content TEXT,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_opinions_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_opinions_author FOREIGN KEY (author_user_id) REFERENCES users (id),
    CONSTRAINT uk_opinions_section_author UNIQUE (project_section_id, author_user_id),
    CONSTRAINT chk_opinions_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT chk_opinions_submitted_content
        CHECK (status <> 'SUBMITTED' OR submitted_content IS NOT NULL),
    CONSTRAINT chk_opinions_submitted_at
        CHECK (status <> 'SUBMITTED' OR submitted_at IS NOT NULL)
);

CREATE INDEX idx_opinions_section_status ON opinions (project_section_id, status);

CREATE TABLE review_links (
    id BIGSERIAL PRIMARY KEY,
    project_section_id BIGINT NOT NULL,
    created_by_user_id BIGINT,
    token_hash VARCHAR(64) NOT NULL,
    section_title_snapshot VARCHAR(200),
    content_snapshot TEXT NOT NULL,
    content_version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_links_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_review_links_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT uk_review_links_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_review_links_status CHECK (status IN ('ACTIVE', 'OUTDATED', 'CLOSED')),
    CONSTRAINT chk_review_links_content_version CHECK (content_version > 0)
);

CREATE INDEX idx_review_links_section_status ON review_links (project_section_id, status);

CREATE TABLE review_submissions (
    id BIGSERIAL PRIMARY KEY,
    review_link_id BIGINT NOT NULL,
    anonymous_reviewer_id VARCHAR(64) NOT NULL,
    understanding_signal VARCHAR(20) NOT NULL,
    summary TEXT,
    reviewer_name VARCHAR(100),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_submissions_link
        FOREIGN KEY (review_link_id) REFERENCES review_links (id),
    CONSTRAINT uk_review_submission_link_reviewer
        UNIQUE (review_link_id, anonymous_reviewer_id),
    CONSTRAINT chk_review_submissions_signal
        CHECK (understanding_signal IN ('CLEAR', 'PARTIAL', 'UNCLEAR'))
);

CREATE TABLE team_reviews (
    id BIGSERIAL PRIMARY KEY,
    project_section_id BIGINT NOT NULL,
    reviewer_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reviewed_content_version INTEGER NOT NULL,
    change_request_reason TEXT,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    outdated BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_team_reviews_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_team_reviews_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users (id),
    CONSTRAINT uk_team_review_section_reviewer UNIQUE (project_section_id, reviewer_user_id),
    CONSTRAINT chk_team_reviews_status
        CHECK (status IN ('PENDING', 'APPROVED', 'CHANGES_REQUESTED')),
    CONSTRAINT chk_team_reviews_content_version CHECK (reviewed_content_version > 0),
    CONSTRAINT chk_team_reviews_version CHECK (version >= 0)
);

-- -----------------------------------------------------------------------------
-- AI job orchestration and usage audit
-- -----------------------------------------------------------------------------

CREATE TABLE ai_jobs (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL,
    project_id BIGINT NOT NULL,
    project_section_id BIGINT,
    requested_by_user_id BIGINT NOT NULL,
    feature VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_snapshot_hash VARCHAR(64) NOT NULL,
    source_version VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    schema_version VARCHAR(100) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    max_output_tokens INTEGER NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    execution_sequence INTEGER NOT NULL,
    retry_of_job_id BIGINT,
    result_id BIGINT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    final_error_type VARCHAR(50),
    safe_error_message VARCHAR(500),
    queued_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    started_at TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    failed_at TIMESTAMP WITHOUT TIME ZONE,
    cancelled_at TIMESTAMP WITHOUT TIME ZONE,
    last_heartbeat_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_jobs_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_ai_jobs_section FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_ai_jobs_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_ai_jobs_retry_of FOREIGN KEY (retry_of_job_id) REFERENCES ai_jobs (id),
    CONSTRAINT uk_ai_jobs_request_id UNIQUE (request_id),
    CONSTRAINT uk_ai_jobs_idempotency_execution
        UNIQUE (idempotency_key, execution_sequence),
    CONSTRAINT chk_ai_jobs_feature CHECK (feature IN
        ('ISSUE_DETECTION', 'OPINION_SYNTHESIS', 'DRAFT_GENERATION', 'DRAFT_REVIEW')),
    CONSTRAINT chk_ai_jobs_status CHECK (status IN
        ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'STALE')),
    CONSTRAINT chk_ai_jobs_final_error_type CHECK (final_error_type IS NULL OR final_error_type IN
        ('INVALID_REQUEST', 'PROVIDER_AUTHENTICATION', 'PROVIDER_PERMISSION', 'RATE_LIMITED',
         'PROVIDER_TIMEOUT', 'PROVIDER_OVERLOADED', 'MODEL_NOT_AVAILABLE', 'PROVIDER_UNAVAILABLE',
         'INVALID_RESPONSE', 'PROVIDER_REFUSAL', 'PROVIDER_MAX_TOKENS', 'JSON_PARSE_FAILED',
         'SCHEMA_VALIDATION_FAILED', 'TYPE_CONVERSION_FAILED', 'SEMANTIC_VALIDATION_FAILED',
         'STALE_INPUT', 'APPLICATION_TIMEOUT', 'WORKER_HEARTBEAT_TIMEOUT', 'PROVIDER_ERROR',
         'INTERNAL_ERROR', 'ORPHANED_REQUEST')),
    CONSTRAINT chk_ai_jobs_max_output_tokens CHECK (max_output_tokens > 0),
    CONSTRAINT chk_ai_jobs_execution_sequence CHECK (execution_sequence > 0),
    CONSTRAINT chk_ai_jobs_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_ai_jobs_status_created ON ai_jobs (status, created_at);
CREATE INDEX idx_ai_jobs_status_started ON ai_jobs (status, started_at);
CREATE INDEX idx_ai_jobs_status_heartbeat ON ai_jobs (status, last_heartbeat_at);
CREATE INDEX idx_ai_jobs_project_created ON ai_jobs (project_id, created_at);
CREATE INDEX idx_ai_jobs_section_created ON ai_jobs (project_section_id, created_at);

CREATE TABLE ai_usage_logs (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL,
    provider_request_id VARCHAR(200),
    provider VARCHAR(30) NOT NULL,
    ai_job_id BIGINT,
    project_id BIGINT NOT NULL,
    project_section_id BIGINT,
    requested_by_user_id BIGINT NOT NULL,
    feature VARCHAR(50) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    input_snapshot_hash VARCHAR(64) NOT NULL,
    input_tokens BIGINT,
    output_tokens BIGINT,
    cache_read_input_tokens BIGINT,
    cache_write_input_tokens BIGINT,
    pricing_version VARCHAR(100),
    input_price_per_million_tokens NUMERIC(24, 12),
    output_price_per_million_tokens NUMERIC(24, 12),
    cache_read_price_per_million_tokens NUMERIC(24, 12),
    cache_write_price_per_million_tokens NUMERIC(24, 12),
    estimated_cost NUMERIC(24, 12),
    request_status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    latency_ms BIGINT,
    attempt_count INTEGER,
    error_type VARCHAR(50),
    error_message VARCHAR(500),
    result_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_usage_logs_job FOREIGN KEY (ai_job_id) REFERENCES ai_jobs (id),
    CONSTRAINT fk_ai_usage_logs_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_ai_usage_logs_section
        FOREIGN KEY (project_section_id) REFERENCES project_sections (id),
    CONSTRAINT fk_ai_usage_logs_requested_by
        FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT uk_ai_usage_logs_request_id UNIQUE (request_id),
    CONSTRAINT chk_ai_usage_logs_feature CHECK (feature IN
        ('ISSUE_DETECTION', 'OPINION_SYNTHESIS', 'DRAFT_GENERATION', 'DRAFT_REVIEW')),
    CONSTRAINT chk_ai_usage_logs_request_status
        CHECK (request_status IN ('REQUESTED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_ai_usage_logs_error_type CHECK (error_type IS NULL OR error_type IN
        ('INVALID_REQUEST', 'PROVIDER_AUTHENTICATION', 'PROVIDER_PERMISSION', 'RATE_LIMITED',
         'PROVIDER_TIMEOUT', 'PROVIDER_OVERLOADED', 'MODEL_NOT_AVAILABLE', 'PROVIDER_UNAVAILABLE',
         'INVALID_RESPONSE', 'PROVIDER_REFUSAL', 'PROVIDER_MAX_TOKENS', 'JSON_PARSE_FAILED',
         'SCHEMA_VALIDATION_FAILED', 'TYPE_CONVERSION_FAILED', 'SEMANTIC_VALIDATION_FAILED',
         'STALE_INPUT', 'APPLICATION_TIMEOUT', 'WORKER_HEARTBEAT_TIMEOUT', 'PROVIDER_ERROR',
         'INTERNAL_ERROR', 'ORPHANED_REQUEST')),
    CONSTRAINT chk_ai_usage_logs_attempt_count
        CHECK (attempt_count IS NULL OR attempt_count > 0)
);

CREATE INDEX idx_ai_usage_logs_project_created ON ai_usage_logs (project_id, created_at);
CREATE INDEX idx_ai_usage_logs_section_created ON ai_usage_logs (project_section_id, created_at);
CREATE INDEX idx_ai_usage_logs_status_started ON ai_usage_logs (request_status, started_at);
CREATE INDEX idx_ai_usage_logs_feature_created ON ai_usage_logs (feature, created_at);
CREATE INDEX idx_ai_usage_logs_job_created ON ai_usage_logs (ai_job_id, created_at);
CREATE INDEX idx_ai_usage_logs_recovery
    ON ai_usage_logs (request_status, feature, started_at);
