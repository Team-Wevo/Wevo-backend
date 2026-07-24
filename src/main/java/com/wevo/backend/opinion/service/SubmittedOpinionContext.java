package com.wevo.backend.opinion.service;

import java.time.LocalDateTime;

/**
 * AI alias 생성 전 단계의 제출 의견 최소 정보.
 *
 * <p>{@code authorReference}는 모델에 직접 보내는 값이 아니라 AI-05가 context-local 별칭을
 * 결정적으로 배정하기 위한 내부 참조다. 이름·이메일·profile은 포함하지 않는다.
 */
public record SubmittedOpinionContext(
        Long opinionId,
        String submittedContent,
        LocalDateTime submittedAt,
        Long authorReference
) {
}
