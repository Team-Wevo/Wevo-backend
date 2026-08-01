package com.wevo.backend.ai.client;

import java.util.Set;
import java.util.Map;

public record StructuredOutputValidationContext(
        Set<Long> allowedResourceIds,
        Set<Long> allowedIssueIds,
        Set<Long> allowedAnswerIds,
        Set<Long> requiredUnresolvedIssueIds,
        Set<String> allowedSourceContents,
        String primarySourceContent,
        Map<Long, String> allowedResourceContents
) {

    public StructuredOutputValidationContext {
        allowedResourceIds = allowedResourceIds == null ? Set.of() : Set.copyOf(allowedResourceIds);
        allowedIssueIds = allowedIssueIds == null ? Set.of() : Set.copyOf(allowedIssueIds);
        allowedAnswerIds = allowedAnswerIds == null ? Set.of() : Set.copyOf(allowedAnswerIds);
        requiredUnresolvedIssueIds = requiredUnresolvedIssueIds == null
                ? Set.of()
                : Set.copyOf(requiredUnresolvedIssueIds);
        allowedSourceContents = allowedSourceContents == null
                ? Set.of()
                : Set.copyOf(allowedSourceContents);
        allowedResourceContents = allowedResourceContents == null
                ? Map.of()
                : Map.copyOf(allowedResourceContents);
    }

    public StructuredOutputValidationContext(
            Set<Long> allowedResourceIds,
            Set<Long> allowedIssueIds,
            Set<Long> allowedAnswerIds,
            Set<Long> requiredUnresolvedIssueIds
    ) {
        this(allowedResourceIds, allowedIssueIds, allowedAnswerIds,
                requiredUnresolvedIssueIds, Set.of(), null, Map.of());
    }

    public StructuredOutputValidationContext(Set<Long> allowedResourceIds) {
        this(allowedResourceIds, Set.of(), Set.of(), Set.of(), Set.of(), null, Map.of());
    }

    public static StructuredOutputValidationContext empty() {
        return new StructuredOutputValidationContext(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), null, Map.of());
    }

    public static StructuredOutputValidationContext forSourceContents(
            String primarySourceContent,
            Set<String> sourceContents
    ) {
        return new StructuredOutputValidationContext(
                Set.of(), Set.of(), Set.of(), Set.of(), sourceContents, primarySourceContent, Map.of());
    }

    public static StructuredOutputValidationContext forResourceContents(Map<Long, String> contents) {
        Map<Long, String> copy = contents == null ? Map.of() : Map.copyOf(contents);
        return new StructuredOutputValidationContext(
                copy.keySet(), Set.of(), Set.of(), Set.of(), Set.copyOf(copy.values()), null, copy);
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
