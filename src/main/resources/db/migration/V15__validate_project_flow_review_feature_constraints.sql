-- V14 adds these expanded checks without scanning the growing AI history tables. Flyway commits
-- V14 first, so validation uses PostgreSQL's weaker validation lock in this separate transaction.
ALTER TABLE ai_jobs
    VALIDATE CONSTRAINT chk_ai_jobs_feature;

ALTER TABLE ai_usage_logs
    VALIDATE CONSTRAINT chk_ai_usage_logs_feature;
