package com.wevo.backend.project.dto.response;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.domain.ProjectSection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 프로젝트 생성 응답. 생성된 프로젝트와 자동 생성된 고정 섹션 목록을 함께 반환한다.
 *
 * @param projectId  생성된 프로젝트 ID
 * @param title      프로젝트 이름
 * @param resultType 결과물 유형
 * @param status     프로젝트 상태
 * @param myRole     요청 사용자의 역할 (생성자 = OWNER)
 * @param sections   자동 생성된 고정 섹션 목록
 */
@Schema(requiredProperties = {"projectId", "title", "resultType", "status", "myRole", "sections"})
public record ProjectCreateResponse(
        Long projectId,
        String title,
        OutputType resultType,
        ProjectStatus status,
        ProjectMemberRole myRole,
        List<SectionSummaryResponse> sections
) {

    public static ProjectCreateResponse of(Project project, List<ProjectSection> sections) {
        return new ProjectCreateResponse(
                project.getId(),
                project.getTitle(),
                project.getResultType(),
                project.getStatus(),
                ProjectMemberRole.OWNER,
                sections.stream().map(SectionSummaryResponse::from).toList()
        );
    }
}
