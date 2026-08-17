package com.wevo.backend.project.domain;

/**
 * 프로젝트가 보관(삭제)됐을 때 발행한다. 다른 도메인이 받아 외부 노출을 함께 끊는다.
 *
 * <p>review 도메인이 이 이벤트를 받아 활성 외부 검토 링크를 닫는다 — project 가
 * {@code ReviewLink} 를 직접 만지지 않도록 이벤트로 갈라 둔다(순환 방지 · CLAUDE.md §6). (#230)
 */
public record ProjectArchivedEvent(Long projectId) {
}
