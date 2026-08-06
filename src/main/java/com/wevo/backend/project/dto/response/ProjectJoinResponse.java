package com.wevo.backend.project.dto.response;

import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 초대 링크로 프로젝트에 참여한 결과 응답.
 *
 * <p>이미 멤버였던 경우에도 기존 역할 그대로 멱등하게 반환한다. (제품 정책서 §2.1)
 *
 * @param projectId    참여한 프로젝트 식별자
 * @param projectTitle 프로젝트 제목
 * @param role         내 역할 (신규 참여 시 {@code MEMBER}, 기존 멤버면 기존 역할)
 */
@Schema(requiredProperties = {"projectId", "projectTitle", "role"})
public record ProjectJoinResponse(
        Long projectId,
        String projectTitle,
        ProjectMemberRole role
) {

    public static ProjectJoinResponse from(ProjectMember member) {
        return new ProjectJoinResponse(
                member.getProject().getId(),
                member.getProject().getTitle(),
                member.getRole()
        );
    }
}
