ALTER TABLE ai_jobs
    ADD COLUMN guardrail_settled_at TIMESTAMP WITHOUT TIME ZONE;

-- 배포 전 생성된 terminal job은 기존 수명주기에서 이미 정산을 시도한 작업이다.
-- 만료되었을 수 있는 과거 Redis ledger를 새 복구 작업이 무한 재처리하지 않도록 backfill한다.
UPDATE ai_jobs
SET guardrail_settled_at = COALESCE(completed_at, updated_at, created_at)
WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'STALE');

CREATE INDEX idx_ai_jobs_guardrail_recovery
    ON ai_jobs (guardrail_settled_at, completed_at)
    WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'STALE');
