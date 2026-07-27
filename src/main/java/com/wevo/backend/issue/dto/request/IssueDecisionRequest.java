package com.wevo.backend.issue.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wevo.backend.issue.domain.IssueDecision;
import com.wevo.backend.issue.domain.IssueOption;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * CONFLICT 쟁점 결정 요청. 선택지 또는 직접 입력 중 정확히 하나만 허용한다. (API_SPEC §3.9.1)
 */
public record IssueDecisionRequest(
        @Size(max = IssueOption.MAX_OPTION_TEXT_LENGTH)
        String selectedOption,
        @Size(max = IssueDecision.MAX_CUSTOM_INPUT_LENGTH)
        String customInput
) {

    @AssertTrue(message = "selectedOption과 customInput 중 하나만 지정해야 합니다.")
    @JsonIgnore
    public boolean isExactlyOneChoiceProvided() {
        return hasText(selectedOption) != hasText(customInput);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
