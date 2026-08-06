-- V18 이 NOT VALID 로 추가한 링크 상태 CHECK 를 기존 행에 대해 검증한다.
-- (V3·V15 와 같은 규약 — 검증 스캔을 앞 마이그레이션의 ACCESS EXCLUSIVE 락 밖으로 뺀다)
--
-- VALIDATE CONSTRAINT 는 SHARE UPDATE EXCLUSIVE 락만 잡아 읽기·쓰기를 막지 않는다.
-- V18 이 커밋된 뒤 별도 마이그레이션으로 실행되므로, 스캔이 진행되는 동안에도 링크 발급·종료·
-- 제출이 계속 처리된다.
ALTER TABLE review_links
    VALIDATE CONSTRAINT chk_review_links_status;
