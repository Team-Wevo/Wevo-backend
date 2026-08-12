-- V22 가 NOT VALID 로 확장한 AiJob 실패 사유 CHECK 를 기존 행에 대해 검증한다.
ALTER TABLE ai_jobs
    VALIDATE CONSTRAINT chk_ai_jobs_final_error_type;
