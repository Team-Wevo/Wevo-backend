package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.Project;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 프로젝트 엔티티나 멤버 정보를 노출하지 않는 AI 입력용 조회 경계. */
@Service
@Transactional(readOnly = true)
public class ProjectAiContextQueryService {

    public ProjectAiContext getProjectContext(VerifiedProjectAccess access) {
        if (access == null) {
            throw new IllegalArgumentException("검증된 프로젝트 접근 정보는 필수입니다.");
        }
        Project project = access.project();
        if (!access.projectId().equals(project.getId())) {
            throw new IllegalStateException("검증된 프로젝트 접근 정보가 일치하지 않습니다.");
        }
        return new ProjectAiContext(
                project.getId(),
                project.getTitle(),
                normalizeOptional(project.getDescription()),
                normalizeOptional(project.getIdeaText()),
                normalizeOptional(project.getAudience()),
                project.getResultType());
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
