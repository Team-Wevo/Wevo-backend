package com.wevo.backend.issue.service;

import java.util.Objects;
import java.util.UUID;

/**
 * 현재 정리 세트를 만든 성공 작업과 내부 세트 식별자의 연결.
 *
 * @param requestId      외부 세트 식별자이자 성공 AI 작업 식별자
 * @param synthesisSetId 내부 {@code synthesis_sets.id}
 */
public record CurrentSynthesisSetReference(
        UUID requestId,
        Long synthesisSetId
) {

    public CurrentSynthesisSetReference {
        Objects.requireNonNull(requestId, "requestId는 필수입니다.");
        Objects.requireNonNull(synthesisSetId, "synthesisSetId는 필수입니다.");
    }
}
