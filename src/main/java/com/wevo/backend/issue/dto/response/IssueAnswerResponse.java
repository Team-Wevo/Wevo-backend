package com.wevo.backend.issue.dto.response;

import com.wevo.backend.issue.domain.IssueAnswer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** GAP 보충 근거 답변 등록 결과. */
@Schema(requiredProperties = {"issueId", "answerId", "answeredAt"})
public record IssueAnswerResponse(
        Long issueId,
        Long answerId,
        LocalDateTime answeredAt
) {

    public static IssueAnswerResponse from(IssueAnswer answer) {
        return new IssueAnswerResponse(
                answer.getIssue().getId(),
                answer.getId(),
                answer.getAnsweredAt());
    }
}
