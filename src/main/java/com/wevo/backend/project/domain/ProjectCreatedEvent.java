package com.wevo.backend.project.domain;

/**
 * 프로젝트가 생성됐을 때 발행한다. AI 도메인이 받아(제목 자동 생성 등) 후속 처리한다.
 *
 * <p>{@code titleAutoAssigned} 는 사용자가 제목을 비워 서버 기본값이 저장됐는지를 뜻한다 —
 * 이 경우에만 AI 제목 제안을 트리거한다(사용자가 직접 넣은 제목은 존중).
 */
public record ProjectCreatedEvent(Long projectId, Long ownerId, boolean titleAutoAssigned) {
}
