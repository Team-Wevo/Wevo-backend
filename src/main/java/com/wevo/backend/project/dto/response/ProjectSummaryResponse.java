package com.wevo.backend.project.dto.response;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;

import java.time.LocalDateTime;

/**
 * 프로젝트 목록 항목. (내가 멤버로 속한 프로젝트)
 *
 * <p>목록은 가볍게 유지하기 위해 섹션 진행 요약을 포함하지 않는다. (상세 조회에서 제공)
 *
 * @param myRole 해당 프로젝트에서의 내 역할
 */
public record ProjectSummaryResponse(
        Long projectId,
        String title,
        OutputType resultType,
        ProjectStatus status,
        ProjectMemberRole myRole,
        LocalDateTime createdAt
) {

    public static ProjectSummaryResponse from(ProjectMember membership) {
        Project project = membership.getProject();
        return new ProjectSummaryResponse(
                project.getId(),
                project.getTitle(),
                project.getResultType(),
                project.getStatus(),
                membership.getRole(),
                project.getCreatedAt()
        );
    }
}
