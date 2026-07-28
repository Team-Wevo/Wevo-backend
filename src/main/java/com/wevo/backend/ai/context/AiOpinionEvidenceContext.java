package com.wevo.backend.ai.context;

/** 초안 생성에 사용되는 synthesis 의견 근거 스냅샷. */
public record AiOpinionEvidenceContext(
        Long opinionId,
        String content
) {
}
