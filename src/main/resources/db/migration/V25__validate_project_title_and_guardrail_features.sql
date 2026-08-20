-- V24 가 NOT VALID 로 추가한 제약을 검증한다. V24 커밋 후 별도 트랜잭션에서 실행되어,
-- PostgreSQL 의 약한 검증 잠금(SHARE UPDATE EXCLUSIVE)만 잡고 기존 행을 확인한다. (V15 동일 패턴)
ALTER TABLE ai_jobs
    VALIDATE CONSTRAINT chk_ai_jobs_feature;

ALTER TABLE ai_usage_logs
    VALIDATE CONSTRAINT chk_ai_usage_logs_feature;
