package com.wevo.backend.project.service;

/**
 * 다른 도메인에 공개하는 프로젝트 멤버 요약. (조회 전용)
 *
 * <p>타 도메인이 {@code ProjectMember} 엔티티나 리포지토리를 직접 참조하지 않도록
 * ({@code CLAUDE.md} §6) 필요한 표시 정보만 담아 내보내는 경계 타입이다.
 *
 * <p><b>역할(role)은 담지 않는다</b> — "프로젝트에 속한 사람 전원"이
 * 대상이라 역할 분기가 없고, 역할이 필요한 화면은 멤버 목록 API(API_SPEC §3.2.8)를 쓴다.
 *
 * @param userId          사용자 ID
 * @param name            표시 이름
 * @param profileImageUrl 프로필 이미지 URL — 없을 수 있다
 */
public record ProjectMemberSummary(Long userId, String name, String profileImageUrl) {
}
