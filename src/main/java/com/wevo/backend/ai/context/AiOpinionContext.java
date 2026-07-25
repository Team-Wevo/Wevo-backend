package com.wevo.backend.ai.context;

/**
 * 제출 의견의 Provider 입력 모델.
 *
 * <p>실제 사용자 식별자는 포함하지 않고 context-local 별칭만 포함한다.</p>
 */
public record AiOpinionContext(
        Long opinionId,
        String authorAlias,
        String submittedContent,
        String submittedAt
) {
}
