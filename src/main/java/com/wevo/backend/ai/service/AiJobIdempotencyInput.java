package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import java.util.regex.Pattern;

public record AiJobIdempotencyInput(
        AiFeature feature,
        Long projectId,
        Long projectSectionId,
        String inputSnapshotHash,
        String sourceVersion,
        String promptVersion,
        String schemaVersion,
        String modelId,
        Integer maxOutputTokens
) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public AiJobIdempotencyInput {
        if (feature == null || projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("feature와 유효한 projectId는 필수입니다.");
        }
        if (projectSectionId != null && projectSectionId <= 0) {
            throw new IllegalArgumentException("projectSectionId는 null 또는 1 이상이어야 합니다.");
        }
        if (inputSnapshotHash == null || !SHA_256_PATTERN.matcher(inputSnapshotHash).matches()) {
            throw new IllegalArgumentException("inputSnapshotHash는 64자리 소문자 SHA-256 hex여야 합니다.");
        }
        requireText(sourceVersion, "sourceVersion");
        requireText(promptVersion, "promptVersion");
        requireText(schemaVersion, "schemaVersion");
        requireText(modelId, "modelId");
        if (maxOutputTokens == null || maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens는 1 이상이어야 합니다.");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException(field + "는 100자 이하여야 합니다.");
        }
    }
}
