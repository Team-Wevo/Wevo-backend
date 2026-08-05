ALTER TABLE ai_jobs
    ADD COLUMN rollout_id varchar(100) NOT NULL DEFAULT 'baseline-legacy',
    ADD COLUMN reasoning_effort varchar(30),
    ADD COLUMN pricing_version varchar(100) NOT NULL DEFAULT 'unpriced',
    ADD COLUMN policy_version varchar(100) NOT NULL DEFAULT 'guardrails-disabled';

UPDATE ai_jobs
SET reasoning_effort = CASE WHEN model_id LIKE 'gpt-%' THEN 'medium' ELSE 'none' END;

ALTER TABLE ai_jobs ALTER COLUMN reasoning_effort SET NOT NULL;

ALTER TABLE ai_usage_logs
    ADD COLUMN rollout_id varchar(100),
    ADD COLUMN reasoning_effort varchar(30),
    ADD COLUMN policy_version varchar(100);

ALTER TABLE ai_jobs
    ALTER COLUMN rollout_id DROP DEFAULT,
    ALTER COLUMN reasoning_effort DROP DEFAULT,
    ALTER COLUMN pricing_version DROP DEFAULT,
    ALTER COLUMN policy_version DROP DEFAULT;

CREATE INDEX idx_ai_jobs_feature_rollout_created
    ON ai_jobs (feature, rollout_id, created_at);

CREATE INDEX idx_ai_usage_logs_feature_rollout_created
    ON ai_usage_logs (feature, rollout_id, created_at);
