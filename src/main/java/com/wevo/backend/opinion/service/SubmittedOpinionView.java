package com.wevo.backend.opinion.service;

import com.wevo.backend.opinion.domain.Opinion;
import java.time.LocalDateTime;

/**
 * AI 의견 정리 입력용으로 다른 도메인에 공개하는 제출 의견의 읽기 전용 투영.
 *
 * <p>작업본({@code content})이 아니라 팀에 공개된 <b>제출본</b>을 담는다(§4.1) — AI 정리는
 * 제출된 내용만 사용한다. 도메인 경계(CLAUDE.md §6)를 위해 {@link Opinion} 엔티티를 그대로
 * 노출하지 않고 이 투영으로만 전달한다.
 *
 * @param opinionId       의견 ID (쟁점-의견 연결·근거 추적용)
 * @param authorId        작성자 사용자 ID
 * @param authorName      작성자 이름 (스냅샷 저장·발췌 표기용)
 * @param submittedContent 팀에 공개된 제출본
 * @param submittedAt     최초 제출 시각 (결정적 chunk 순서용)
 */
public record SubmittedOpinionView(
        Long opinionId,
        Long authorId,
        String authorName,
        String submittedContent,
        LocalDateTime submittedAt
) {

    static SubmittedOpinionView from(Opinion opinion) {
        return new SubmittedOpinionView(
                opinion.getId(),
                opinion.getAuthor().getId(),
                opinion.getAuthor().getName(),
                opinion.getSubmittedContentOrLegacy(),
                opinion.getSubmittedAt()
        );
    }
}
