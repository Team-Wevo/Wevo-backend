package com.wevo.backend.issue.service;

/** current synthesis의 합의점 또는 쟁점이 참조한 제출 의견 스냅샷. */
public record SynthesisOpinionEvidenceContext(
        Long opinionId,
        String authorNameSnapshot,
        String content
) {
}
