package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;

public record AiJobCreateCommand(
        Project project,
        ProjectSection projectSection,
        User requestedBy,
        AiFeature feature,
        String inputSnapshotHash,
        String sourceVersion,
        String promptVersion,
        String schemaVersion,
        String modelId,
        Integer maxOutputTokens
) {

    public AiJobCreateCommand {
        if (project == null || requestedBy == null || feature == null) {
            throw new IllegalArgumentException("project, requestedBy, feature는 필수입니다.");
        }
        if (project.getId() == null || requestedBy.getId() == null) {
            throw new IllegalArgumentException("영속화된 project와 requestedBy가 필요합니다.");
        }
        if (projectSection != null) {
            if (projectSection.getId() == null || projectSection.getProject() == null
                    || !project.getId().equals(projectSection.getProject().getId())) {
                throw new IllegalArgumentException("projectSection은 요청 project에 속해야 합니다.");
            }
        }
        new AiJobIdempotencyInput(
                feature,
                project.getId(),
                projectSection == null ? null : projectSection.getId(),
                inputSnapshotHash,
                sourceVersion,
                promptVersion,
                schemaVersion,
                modelId,
                maxOutputTokens
        );
    }

    public AiJobIdempotencyInput idempotencyInput() {
        return new AiJobIdempotencyInput(
                feature,
                project.getId(),
                projectSection == null ? null : projectSection.getId(),
                inputSnapshotHash,
                sourceVersion,
                promptVersion,
                schemaVersion,
                modelId,
                maxOutputTokens
        );
    }
}
