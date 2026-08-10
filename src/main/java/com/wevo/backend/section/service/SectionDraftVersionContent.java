package com.wevo.backend.section.service;

import java.time.LocalDateTime;

/**
 * 초안 한 버전의 본문. (조회 전용 — API_SPEC §3.7.9)
 *
 * <p>{@link SectionDraftVersionSummary} 와 같은 이유로 편집자를 스칼라로 담는다 —
 * 응답 DTO 가 {@code User} 엔티티에 의존하지 않게 하기 위함이다. (CLAUDE.md §6)
 *
 * @param version      본문 버전
 * @param content      해당 버전의 본문 — 저장된 값이 없으면 {@code null}
 * @param editorUserId 저장한 사용자 ID — 기록이 없으면 {@code null}
 * @param editorName   저장한 사용자의 표시 이름 — 기록이 없으면 {@code null}
 * @param savedAt      저장 시각
 */
public record SectionDraftVersionContent(
        Integer version,
        String content,
        Long editorUserId,
        String editorName,
        LocalDateTime savedAt
) {
}
