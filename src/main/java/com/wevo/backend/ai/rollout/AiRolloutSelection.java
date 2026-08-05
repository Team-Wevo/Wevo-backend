package com.wevo.backend.ai.rollout;

public record AiRolloutSelection(
        String rolloutId,
        String modelId,
        String promptVersion,
        String schemaVersion,
        String reasoningEffort,
        String pricingVersion,
        String policyVersion
) {
}
