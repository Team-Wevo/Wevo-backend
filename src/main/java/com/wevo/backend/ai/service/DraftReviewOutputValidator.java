package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.dto.model.DraftReviewFindingOutput;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.dto.model.DraftReviewRewriteOutput;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** finding snippet과 전체 rewrite의 제품 의미 계약을 검증한다. */
@Component
public class DraftReviewOutputValidator implements StructuredOutputValidator<DraftReviewOutput> {

    @Override
    public void validate(
            DraftReviewOutput output,
            StructuredOutputValidationContext context
    ) {
        if (output == null || context == null || output.findings() == null) {
            throw reject();
        }
        validateRewrite(output.rewrite());
        for (DraftReviewFindingOutput finding : output.findings()) {
            validateFinding(finding, context);
        }

        String currentContent = context.primarySourceContent();
        if (currentContent == null || !context.allowedSourceContents().contains(currentContent)) {
            throw reject();
        }
        if (output.findings().isEmpty()) {
            if (output.rewrite().changedCount() != 0
                    || !Objects.equals(currentContent, output.rewrite().content())) {
                throw reject();
            }
        } else if (output.rewrite().changedCount() <= 0
                || Objects.equals(currentContent, output.rewrite().content())) {
            throw reject();
        }
    }

    private void validateFinding(
            DraftReviewFindingOutput finding,
            StructuredOutputValidationContext context
    ) {
        if (finding == null
                || finding.type() == null
                || isBlank(finding.targetExcerpt())
                || isBlank(finding.comment())
                || isBlank(finding.suggestion())) {
            throw reject();
        }
        boolean excerptExists = context.allowedSourceContents().stream()
                .anyMatch(content -> content.contains(finding.targetExcerpt()));
        if (!excerptExists) {
            throw reject();
        }
    }

    private void validateRewrite(DraftReviewRewriteOutput rewrite) {
        if (rewrite == null
                || isBlank(rewrite.content())
                || rewrite.content().length() > SectionDraftSaveRequest.MAX_CONTENT_LENGTH
                || rewrite.changedCount() < 0) {
            throw reject();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private StructuredOutputSemanticException reject() {
        return new StructuredOutputSemanticException();
    }
}
