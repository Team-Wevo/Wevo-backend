-- 외부 검토 링크에 유효 기간(만료일)을 둔다. (API_SPEC §3.5.1)
--
-- 지금까지 링크 종료는 본문 수정(OUTDATED) · 재발급/수동 종료(CLOSED) 뿐이라, 팀장이 "이 링크는
-- N일까지만 열어둔다"를 미리 정할 수 없었다. 발급 시점에 마지막 유효 날짜를 지정할 수 있게 한다.
--
-- 날짜(DATE)로 저장하는 이유 — 유효 기간은 팀 합의로 "날짜 단위"로만 지정한다(시:분 미지정).
-- expires_on 은 링크가 살아 있는 **마지막 날**이며, 그 날 23:59:59(KST)까지 제출을 받는다.
-- NULL 은 기간 제한 없음(기존 링크의 동작)이라 기존 행은 그대로 두면 된다.
ALTER TABLE review_links ADD COLUMN expires_on DATE;

-- 유효 기간이 지난 링크의 종결 상태. OUTDATED(본문 수정) · CLOSED(종료)와 사유가 다르므로
-- 별도 값으로 남긴다 — 외부 검토자·팀장에게 안내할 문구가 각각 다르다.
--
-- "발급일보다 최소 1일 뒤" 규칙은 DB CHECK 로 두지 않는다. created_at 은 서버 JVM 시간대로
-- 기록되는 반면 expires_on 은 KST 기준 날짜라, 자정 부근에서 정상 요청이 제약 위반(500)으로
-- 튕길 수 있다. 이 규칙은 발급 시 서비스에서 KST 기준으로 검증한다. (422 C002)
--
-- NOT VALID 로 추가하고 검증은 V19 로 분리한다 (V2→V3, V14→V15 와 같은 규약 — README 참고).
-- ADD CONSTRAINT 는 ACCESS EXCLUSIVE 락을 쥔 채 기존 행을 전부 스캔하므로, 그동안 링크 발급·종료·
-- 제출이 대기한다. NOT VALID 는 스캔을 건너뛰되 **새로 들어오거나 수정되는 행에는 제약을 그대로
-- 적용**하므로 잘못된 status 가 새로 저장될 여지는 없다.
--
-- 여기서는 스캔이 특히 무의미하다 — 새 CHECK 는 기존 CHECK 의 상위집합(기존 3개 값 + EXPIRED)이라,
-- 이전 제약을 통과해 저장된 행은 정의상 새 제약도 만족한다.
ALTER TABLE review_links DROP CONSTRAINT chk_review_links_status;
ALTER TABLE review_links ADD CONSTRAINT chk_review_links_status
    CHECK (status IN ('ACTIVE', 'OUTDATED', 'CLOSED', 'EXPIRED')) NOT VALID;
