package com.wevo.backend.ai.dto.model;

import com.wevo.backend.issue.domain.IssueType;
import java.util.List;

/**
 * AI가 제안한 단일 쟁점 후보.
 *
 * <p>타입별 필드 조합과 길이·개수·근거 무결성은
 * {@code IssueDetectionOutputValidator}가 Provider 응답 경계에서 검증한다.</p>
 */
public record IssueDetectionIssueOutput(
        IssueType type,
        String description,
        List<Long> evidenceOpinionIds,
        String question,
        List<String> options
) {

    public IssueDetectionIssueOutput {
        evidenceOpinionIds = evidenceOpinionIds == null ? null : List.copyOf(evidenceOpinionIds);
        options = options == null ? null : List.copyOf(options);
    }
}
