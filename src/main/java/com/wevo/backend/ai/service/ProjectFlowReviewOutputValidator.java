package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.dto.model.ProjectFlowFindingOutput;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** section allowlist, section별 실제 excerpt, cross-section·중복 finding을 검증한다. */
@Component
public class ProjectFlowReviewOutputValidator
        implements StructuredOutputValidator<ProjectFlowReviewOutput> {

    @Override
    public void validate(ProjectFlowReviewOutput output, StructuredOutputValidationContext context) {
        if (output == null || output.findings() == null
                || output.findings().size() > ProjectFlowReviewContract.MAX_FINDING_COUNT) {
            throw reject();
        }
        Set<String> identities = new HashSet<>();
        for (ProjectFlowFindingOutput finding : output.findings()) {
            if (finding == null || finding.type() == null || finding.sections() == null
                    || finding.sections().size() < 2) {
                throw reject();
            }
            requireText(finding.description(), ProjectFlowReviewContract.MAX_DESCRIPTION_LENGTH);
            requireText(finding.suggestion(), ProjectFlowReviewContract.MAX_SUGGESTION_LENGTH);
            Set<Long> sectionIds = new HashSet<>();
            List<String> referenceIdentities = new ArrayList<>();
            for (var reference : finding.sections()) {
                if (reference == null || !sectionIds.add(reference.sectionId())) {
                    throw reject();
                }
                context.requireAllowedResourceId(reference.sectionId());
                requireText(reference.targetExcerpt(), ProjectFlowReviewContract.MAX_EXCERPT_LENGTH);
                String content = context.allowedResourceContents().get(reference.sectionId());
                if (content == null || !content.contains(reference.targetExcerpt())) {
                    throw reject();
                }
                referenceIdentities.add(reference.sectionId() + ":" + reference.targetExcerpt());
            }
            referenceIdentities.sort(String::compareTo);
            String identity = finding.type().name() + "|" + String.join("|", referenceIdentities);
            if (!identities.add(identity)) {
                throw reject();
            }
        }
    }

    private void requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw reject();
        }
    }

    private StructuredOutputSemanticException reject() {
        return new StructuredOutputSemanticException();
    }
}
