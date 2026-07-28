package com.wevo.backend.issue.dto.request;

import com.wevo.backend.issue.domain.IssueAnswer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 지목된 팀원이 제출하는 GAP 보충 근거 답변. (API_SPEC §3.9.3) */
public record IssueAnswerRequest(
        @NotBlank
        @Size(max = IssueAnswer.MAX_CONTENT_LENGTH)
        String content
) {
}
