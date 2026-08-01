-- 외부 검토자의 추가 코멘트. (정책서 §6.2.3 "추가 코멘트는 선택 입력한다")
-- 이해한 핵심 한 문장(summary)과 별개 입력이며 항상 선택이라 NULL 을 허용한다.
-- 기존 행은 코멘트가 없던 제출이므로 NULL 로 남는다. (기본값·백필 없음)
ALTER TABLE review_submissions
    ADD COLUMN reviewer_comment TEXT;
