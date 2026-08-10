package com.wevo.backend.section.service;

import java.time.LocalDateTime;

/**
 * 초안 버전 한 건의 요약. (조회 전용 — API_SPEC §3.7.8)
 *
 * <p>편집자를 <b>사용자 엔티티가 아니라 스칼라 값</b>으로 담는다. 응답 DTO 가 조립에 필요한 것은
 * 표시 이름 하나인데, 그걸 얻자고 {@code User} 를 넘기면 section 의 DTO 가 user 도메인 엔티티에
 * 의존하게 된다 (CLAUDE.md §6 — 도메인 간 접근).
 *
 * <p><b>본문을 담지 않는다</b> — 목록은 "어느 버전을 열어볼지" 고르는 화면이라 본문이 필요 없고,
 * 이력이 길어질수록 읽지도 않을 본문을 통째로 메모리에 올리는 비용이 커진다. 대신 길이만
 * 조회 시점에 계산해 담는다.
 *
 * @param version       본문 버전
 * @param contentLength 본문 글자 수 — 본문 없이도 내용이 급감한 지점을 짚기 위한 값
 * @param editorUserId  저장한 사용자 ID — 기록이 없으면 {@code null}
 * @param editorName    저장한 사용자의 표시 이름 — 기록이 없으면 {@code null}
 * @param savedAt       저장 시각
 */
public record SectionDraftVersionSummary(
        Integer version,
        int contentLength,
        Long editorUserId,
        String editorName,
        LocalDateTime savedAt
) {
}
