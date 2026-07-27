-- Indexes for the synthesis read path (API_SPEC 3.8.2).
-- GET synthesis is polled, and both of its lookups filter by section + feature and then take the
-- newest row. V1 only indexes (project_section_id, created_at), which supports neither the feature
-- filter nor the ordering, so each poll degrades into a scan-and-sort as job history grows.

-- Latest execution for a section's feature: latestJob.
CREATE INDEX idx_ai_jobs_section_feature_queued
    ON ai_jobs (project_section_id, feature, queued_at DESC, id DESC);

-- Latest successful execution for a section's feature: the current synthesis set's source.
-- `status` stays a key column instead of becoming a partial index predicate (WHERE status =
-- 'SUCCEEDED'). The query binds status as a parameter, and PostgreSQL can only use a partial index
-- when it can prove the predicate holds at plan time, which a generic plan for a bind parameter
-- cannot do. A key column is used regardless of how the statement is planned.
CREATE INDEX idx_ai_jobs_section_feature_status_completed
    ON ai_jobs (project_section_id, feature, status, completed_at DESC, id DESC);

-- Plain CREATE INDEX (not CONCURRENTLY): Flyway runs each migration in a transaction, and
-- CONCURRENTLY cannot run inside one. At MVP scale ai_jobs is small, so the brief write lock is
-- acceptable. Revisit with CONCURRENTLY outside Flyway if this table ever grows large in production.
