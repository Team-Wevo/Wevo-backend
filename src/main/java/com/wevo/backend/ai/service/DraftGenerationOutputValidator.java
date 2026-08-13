package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 초안 본문 불변식과 생성 근거 allowlist를 검증한다. */
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
        requireAllowed(output.evidenceOpinionIds(),
                context::requireAllowedResourceId);
        requireAllowed(output.evidenceIssueIds(),
                context::requireAllowedIssueId);
        requireAllowed(output.evidenceAnswerIds(),
                context::requireAllowedAnswerId);
        requireExact(output.unresolvedGapIssueIds(), context.requiredUnresolvedIssueIds(),
                context::requireAllowedIssueId);
        for (Long issueId : context.requiredUnresolvedIssueIds()) {
            if (!output.content().contains(DraftGenerationContract.unresolvedGapMarker(issueId))) {
                throw reject();
            }
        }
    }

    private void requireAllowed(
            List<Long> values,
            java.util.function.Consumer<Long> allowlistCheck
    ) {
        if (values == null) {
            throw reject();
        }
        Set<Long> unique = new HashSet<>();
        for (Long value : values) {
            if (!unique.add(value)) {
                throw reject();
            }
            allowlistCheck.accept(value);
        }
    }

    private void requireExact(
            List<Long> values,
            Set<Long> expected,
            java.util.function.Consumer<Long> allowlistCheck
    ) {
        if (values == null) {
            throw reject();
        }
        Set<Long> unique = new HashSet<>();
        for (Long value : values) {
            if (!unique.add(value)) {
                throw reject();
            }
            allowlistCheck.accept(value);
        }
        if (!unique.equals(expected)) {
            throw reject();
        }
    }

    private StructuredOutputSemanticException reject() {
        return new StructuredOutputSemanticException();
    }
}
