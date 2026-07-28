-- 섹션당 ACTIVE 외부 검토 링크는 최대 1개만 존재하도록 DB 레벨에서 강제한다. (대체 발급 무결성)
--
-- 지금까지 "섹션당 ACTIVE 1개"는 재발급 시 기존 ACTIVE 링크를 CLOSED 로 닫는 애플리케이션
-- 로직에만 의존했다. 동시성·회귀로 중복 ACTIVE 가 생기면 상태 복구 조회
-- (GET .../review-links/current)가 어느 링크를 현재 링크로 볼지 모호해지므로, 부분 유니크
-- 인덱스로 불변식을 못 박는다.
--
-- 부분 인덱스: status = 'ACTIVE' 인 행에 대해서만 project_section_id 유일성을 요구한다.
-- OUTDATED/CLOSED 링크는 이력으로 섹션당 여러 개 남을 수 있으므로 인덱스 대상에서 제외한다.
CREATE UNIQUE INDEX uk_review_links_active_per_section
    ON review_links (project_section_id)
    WHERE status = 'ACTIVE';
