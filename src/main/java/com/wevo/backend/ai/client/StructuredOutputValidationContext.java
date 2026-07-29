package com.wevo.backend.ai.client;

import java.util.Set;

public record StructuredOutputValidationContext(
        Set<Long> allowedResourceIds,
        Set<Long> allowedIssueIds,
        Set<Long> allowedAnswerIds,
        Set<Long> requiredUnresolvedIssueIds
) {

    public StructuredOutputValidationContext {
        allowedResourceIds = allowedResourceIds == null ? Set.of() : Set.copyOf(allowedResourceIds);
        allowedIssueIds = allowedIssueIds == null ? Set.of() : Set.copyOf(allowedIssueIds);
        allowedAnswerIds = allowedAnswerIds == null ? Set.of() : Set.copyOf(allowedAnswerIds);
        requiredUnresolvedIssueIds = requiredUnresolvedIssueIds == null
                ? Set.of()
                : Set.copyOf(requiredUnresolvedIssueIds);
    }

    public StructuredOutputValidationContext(Set<Long> allowedResourceIds) {
        this(allowedResourceIds, Set.of(), Set.of(), Set.of());
    }

    public static StructuredOutputValidationContext empty() {
        return new StructuredOutputValidationContext(Set.of(), Set.of(), Set.of(), Set.of());
    }

    public void requireAllowedResourceId(Long resourceId) {
        if (resourceId == null || !allowedResourceIds.contains(resourceId)) {
            throw new StructuredOutputSemanticException();
        }
    }

    public void requireAllowedIssueId(Long issueId) {
        if (issueId == null || !allowedIssueIds.contains(issueId)) {
            throw new StructuredOutputSemanticException();
        }
    }

    public void requireAllowedAnswerId(Long answerId) {
        if (answerId == null || !allowedAnswerIds.contains(answerId)) {
            throw new StructuredOutputSemanticException();
        }
    }
}
