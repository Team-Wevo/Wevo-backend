-- 섹션당 ACTIVE 외부 검토 링크는 최대 1개만 존재하도록 DB 레벨에서 강제한다. (대체 발급 무결성)
--
-- 지금까지 "섹션당 ACTIVE 1개"는 재발급 시 기존 ACTIVE 링크를 CLOSED 로 닫는 애플리케이션
-- 로직에만 의존했다. 동시성·회귀로 중복 ACTIVE 가 생기면 상태 복구 조회
-- (GET .../review-links/current)가 어느 링크를 현재 링크로 볼지 모호해지므로, 부분 유니크
-- 인덱스로 불변식을 못 박는다.

-- 선(先) 정리 — 인덱스 생성 전에 섹션당 ACTIVE 가 2개 이상 남아 있는 레거시/경합 데이터를 만료시킨다.
-- 이 정리가 없으면, 중복 ACTIVE 가 하나라도 있는 DB 에서는 아래 UNIQUE INDEX 생성이 실패해
-- Flyway 마이그레이션 전체가 중단된다(배포 중단).
-- 각 섹션에서 가장 최근에 발급된(id 최대) ACTIVE 하나만 남기고 나머지는 CLOSED 로 만료시킨다.
-- (id 는 BIGSERIAL 로 발급 순서와 단조 증가하므로 최신 발급 = 현재 링크로 본다. 대체 발급 의미와 일치)
UPDATE review_links
SET status = 'CLOSED'
WHERE status = 'ACTIVE'
  AND id NOT IN (
      SELECT MAX(id)
      FROM review_links
      WHERE status = 'ACTIVE'
      GROUP BY project_section_id
  );

-- 부분 인덱스: status = 'ACTIVE' 인 행에 대해서만 project_section_id 유일성을 요구한다.
-- OUTDATED/CLOSED 링크는 이력으로 섹션당 여러 개 남을 수 있으므로 인덱스 대상에서 제외한다.
CREATE UNIQUE INDEX uk_review_links_active_per_section
    ON review_links (project_section_id)
    WHERE status = 'ACTIVE';
