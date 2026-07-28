-- customInput의 길이는 공백을 제외한 문자 수로 검증한다.
-- 원문 공백을 보존하면서 비공백 200자를 허용하기 위해 물리 길이 제한을 제거한다.
ALTER TABLE issue_decisions
    ALTER COLUMN custom_input TYPE TEXT;
