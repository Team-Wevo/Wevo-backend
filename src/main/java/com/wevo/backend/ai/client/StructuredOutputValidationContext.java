package com.wevo.backend.ai.client;

import java.util.Set;

public record StructuredOutputValidationContext(Set<Long> allowedResourceIds) {

    public StructuredOutputValidationContext {
        allowedResourceIds = allowedResourceIds == null ? Set.of() : Set.copyOf(allowedResourceIds);
    }

    public static StructuredOutputValidationContext empty() {
        return new StructuredOutputValidationContext(Set.of());
    }

    public void requireAllowedResourceId(Long resourceId) {
        if (resourceId == null || !allowedResourceIds.contains(resourceId)) {
            throw new StructuredOutputSemanticException();
        }
    }
}
