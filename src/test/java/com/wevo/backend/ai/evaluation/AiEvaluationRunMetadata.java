package com.wevo.backend.ai.evaluation;

import java.time.Instant;

public record AiEvaluationRunMetadata(
        String datasetVersion,
        String providerId,
        String modelId,
        String endpointType,
        String reasoningEffort,
        String promptVersion,
        String schemaVersion,
        Instant executedAt,
        Double temperature,
        int maxOutputTokens,
        String gitCommit
) {

    public AiEvaluationRunMetadata {
        requireText(datasetVersion, "datasetVersion");
        requireText(providerId, "providerId");
        requireText(modelId, "modelId");
        endpointType = defaultText(endpointType, "unknown");
        reasoningEffort = defaultText(reasoningEffort, "unknown");
        requireText(promptVersion, "promptVersion");
        requireText(schemaVersion, "schemaVersion");
        requireText(gitCommit, "gitCommit");
        if (executedAt == null || maxOutputTokens <= 0) {
            throw new IllegalArgumentException("실행 시각과 maxOutputTokens는 필수입니다.");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
