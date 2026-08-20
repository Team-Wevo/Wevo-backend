-- ai_jobs / ai_usage_logs 의 feature CHECK 제약에 두 기능을 추가한다.
--   PROJECT_TITLE_SUGGESTION — 제목 자동 생성(#316). enum·코드는 배포됐으나 제약을 빠뜨려
--     운영에서 job INSERT 가 조용히 제약 위반으로 막혀 제목이 되채워지지 않았다.
--   OPINION_CONTENT_GUARDRAIL — 의견 내용 가드레일. 같은 사고를 미리 막으려 선반영한다
--     (가드레일은 jobless 라 ai_usage_logs 만 기록하지만, 두 테이블 목록을 일치시켜 둔다).
--
-- 큰 AI 히스토리 테이블을 스캔하지 않도록 NOT VALID 로 추가하고, 검증은 별도 트랜잭션(V25)에서
-- 수행한다. NOT VALID 는 기존 행 검사만 건너뛰며 신규 INSERT 는 즉시 이 제약으로 검사된다. (V14 동일 패턴)
ALTER TABLE ai_jobs DROP CONSTRAINT chk_ai_jobs_feature;
ALTER TABLE ai_jobs ADD CONSTRAINT chk_ai_jobs_feature CHECK (feature IN (
    'ISSUE_DETECTION', 'OPINION_SYNTHESIS', 'DRAFT_GENERATION', 'DRAFT_REVIEW',
    'AUTHOR_INTENT_EXTRACTION', 'REVIEW_INTENT_COMPARISON', 'OPINION_CLUSTERING',
    'PROJECT_FLOW_REVIEW', 'PROJECT_TITLE_SUGGESTION', 'OPINION_CONTENT_GUARDRAIL'
)) NOT VALID;

ALTER TABLE ai_usage_logs DROP CONSTRAINT chk_ai_usage_logs_feature;
ALTER TABLE ai_usage_logs ADD CONSTRAINT chk_ai_usage_logs_feature CHECK (feature IN (
    'ISSUE_DETECTION', 'OPINION_SYNTHESIS', 'DRAFT_GENERATION', 'DRAFT_REVIEW',
    'AUTHOR_INTENT_EXTRACTION', 'REVIEW_INTENT_COMPARISON', 'OPINION_CLUSTERING',
    'PROJECT_FLOW_REVIEW', 'PROJECT_TITLE_SUGGESTION', 'OPINION_CONTENT_GUARDRAIL'
)) NOT VALID;
