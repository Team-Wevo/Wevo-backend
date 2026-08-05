-- 프로젝트 목록 카드에 "마지막으로 작업한 섹션"을 표시하고, 목록을 최근 작업순으로 정렬하기 위한 컬럼.
-- (API_SPEC §3.2.2 — 이슈 #179, #146)
--
-- 조회 시점에 여러 테이블(section_drafts, opinions, section_status_histories)의 시각을 훑어
-- 계산하지 않고, 활동이 생길 때 여기에 기록한다. 목록 조회는 프로젝트 수만큼 반복되는 화면이라
-- 읽기를 가볍게 유지한다.
--
-- project_sections.updated_at 을 쓰지 못하는 이유: 초안 본문은 section_drafts 에 별도로 쌓이므로
-- 팀원이 초안을 고쳐도 이 행은 바뀌지 않는다. 반대로 아무도 손대지 않은 섹션이 내부 플래그
-- (drift_status, ai_check_status 등) 변경만으로 "최근 작업"으로 잡힐 수 있다.
ALTER TABLE project_sections
    ADD COLUMN last_activity_at TIMESTAMP;

-- 기존 행은 생성 시각으로 채운다. 활동 이력이 없으므로 "아직 아무 일도 없었다"에 가장 가깝다.
UPDATE project_sections
SET last_activity_at = created_at
WHERE last_activity_at IS NULL;

ALTER TABLE project_sections
    ALTER COLUMN last_activity_at SET NOT NULL;

-- 목록 조회는 "내 프로젝트들의 섹션"을 한 번에 읽어 프로젝트별 최신 활동을 고른다.
CREATE INDEX idx_project_sections_project_last_activity
    ON project_sections (project_id, last_activity_at DESC);
