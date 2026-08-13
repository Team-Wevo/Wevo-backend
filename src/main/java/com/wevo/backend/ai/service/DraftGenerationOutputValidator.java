package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import org.springframework.stereotype.Component;

/** 초안 본문 불변식과 서버 context 기반 미답변 GAP 표시를 검증한다. */
@Component
public class DraftGenerationOutputValidator
        implements StructuredOutputValidator<DraftGenerationOutput> {

    @Override
    public void validate(
            DraftGenerationOutput output,
            StructuredOutputValidationContext context
    ) {
        if (output == null || context == null
                || output.content() == null
                || output.content().isBlank()
                || output.content().length() > SectionDraftSaveRequest.MAX_CONTENT_LENGTH) {
            throw reject();
        }
        for (Long issueId : context.requiredUnresolvedIssueIds()) {
            if (!output.content().contains(DraftGenerationContract.unresolvedGapMarker(issueId))) {
                throw reject();
            }
        }
    }

    private StructuredOutputSemanticException reject() {
        return new StructuredOutputSemanticException();
    }
}
