package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;

public record AiUsageStartCommand(
        Project project,
        ProjectSection projectSection,
        User requestedBy,
        AiFeature feature,
        String promptVersion,
        String inputSnapshotHash
) {

    public AiUsageStartCommand {
        if (project == null || requestedBy == null || feature == null) {
            throw new IllegalArgumentException("project, requestedBy, feature는 필수입니다.");
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalArgumentException("promptVersion은 필수입니다.");
        }
        if (promptVersion.length() > 100) {
            throw new IllegalArgumentException("promptVersion은 100자 이하여야 합니다.");
        }
        if (inputSnapshotHash == null || inputSnapshotHash.isBlank()) {
            throw new IllegalArgumentException("inputSnapshotHash는 필수입니다.");
        }
        if (inputSnapshotHash.length() > 64) {
            throw new IllegalArgumentException("inputSnapshotHash는 64자 이하여야 합니다.");
        }
    }
}
