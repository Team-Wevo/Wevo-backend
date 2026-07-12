package com.wevo.backend.project.dto.response;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;

import java.util.List;

/**
 * 프로젝트 상세. 내 역할·멤버 수·섹션 진행 요약을 함께 제공한다.
 *
 * @param memberCount     프로젝트 멤버 수
 * @param sectionProgress 확정된 섹션 수 / 전체 섹션 수
 */
public record ProjectDetailResponse(
        Long projectId,
        String title,
        String description,
        String ideaText,
        OutputType resultType,
        String audience,
        ProjectStatus status,
        ProjectMemberRole myRole,
        long memberCount,
        SectionProgress sectionProgress
) {

    /**
     * @param total     전체 섹션 수 (유형별 고정 6개)
     * @param confirmed CONFIRMED 상태인 섹션 수
     */
    public record SectionProgress(int total, int confirmed) {
    }

    public static ProjectDetailResponse of(Project project,
                                           ProjectMemberRole myRole,
                                           long memberCount,
                                           List<ProjectSection> sections) {
        int confirmed = (int) sections.stream()
                .filter(section -> section.getStatus() == ProjectSectionStatus.CONFIRMED)
                .count();

        return new ProjectDetailResponse(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getIdeaText(),
                project.getResultType(),
                project.getAudience(),
                project.getStatus(),
                myRole,
                memberCount,
                new SectionProgress(sections.size(), confirmed)
        );
    }
}
