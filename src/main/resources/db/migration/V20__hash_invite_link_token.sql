-- 초대 링크 토큰을 원문 대신 SHA-256 해시로 저장한다. (#174)
--
-- DB 가 유출되면 원문 토큰을 그대로 읽어 프로젝트에 합류할 수 있었다. 외부 검토 링크
-- (review_links.token_hash)는 이미 해시로 저장하고 있어 두 링크의 보안 수준이 달랐다.
--
-- 기존 행은 지우지 않고 그대로 둔다. 운영 중인 DB 라 살아 있는 링크를 지울 이유가 없다.
-- 남는 값은 32자 평문 UUID 라 64자 SHA-256 해시와 절대 일치하지 않으므로, 그 행으로는
-- 아무도 합류할 수 없다(P003) — 해시 저장으로 옛 링크가 무효가 되는 것은 이 변경의 목적이다.
-- 팀장이 초대 링크를 다시 발급하면 InviteService 가 그 행의 해시를 현재 값으로 맞춰
-- 되살린다(InviteLink#refreshTokenHash). 새 행이 생기지 않으므로 중복도 남지 않는다.
--
-- 컬럼 길이를 64 로 줄여도 기존 32자 값은 잘리지 않는다.
ALTER TABLE invite_links DROP CONSTRAINT uk_invite_links_token;
ALTER TABLE invite_links RENAME COLUMN token TO token_hash;
ALTER TABLE invite_links ALTER COLUMN token_hash TYPE VARCHAR(64);
ALTER TABLE invite_links ADD CONSTRAINT uk_invite_links_token_hash UNIQUE (token_hash);
