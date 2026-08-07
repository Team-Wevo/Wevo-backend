-- 초대 링크 토큰을 원문 대신 SHA-256 해시로 저장한다. (#174)
--
-- DB 가 유출되면 원문 토큰을 그대로 읽어 프로젝트에 합류할 수 있었다. 외부 검토 링크
-- (review_links.token_hash)는 이미 해시로 저장하고 있어 두 링크의 보안 수준이 달랐다.
--
-- 기존 행은 변환하지 않고 지운다. 원문 토큰은 이제 HMAC(비밀키, project_id)로 파생하므로
-- 예전 난수 토큰과 값이 다르고, SQL 만으로는 새 값을 계산할 수도 없다(비밀키가 애플리케이션에
-- 있다). 남겨두면 어떤 요청과도 매칭되지 않는 죽은 행이 유니크 제약만 차지한다.
-- 초대 링크는 OWNER 가 API 를 다시 호출하면 즉시 재발급되므로 복구 비용이 없다.
DELETE FROM invite_links;

ALTER TABLE invite_links DROP CONSTRAINT uk_invite_links_token;
ALTER TABLE invite_links RENAME COLUMN token TO token_hash;
ALTER TABLE invite_links ALTER COLUMN token_hash TYPE VARCHAR(64);
ALTER TABLE invite_links ADD CONSTRAINT uk_invite_links_token_hash UNIQUE (token_hash);
