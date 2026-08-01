ALTER TABLE ai_usage_logs
    ADD COLUMN reasoning_tokens BIGINT;

ALTER TABLE ai_usage_logs
    ADD CONSTRAINT ck_ai_usage_logs_reasoning_tokens_non_negative
        CHECK (reasoning_tokens IS NULL OR reasoning_tokens >= 0);
