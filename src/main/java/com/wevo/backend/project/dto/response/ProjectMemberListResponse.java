package com.wevo.backend.project.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프로젝트 멤버 목록. 팀원 표시·의견 작성자 매칭·검토 대상자 표시에 사용한다. (API_SPEC §3.2.8)
 *
 * <p>정원이 최대 4명({@link Project#MAX_MEMBERS} — 정책서 §2.1)이라 페이지네이션 없이 전원을 반환한다.
 *
 * @param memberCount 현재 참여 인원
 * @param maxMembers  최대 인원 (정책서 §2.1 — 4)
 * @param members     참여자 목록 (OWNER 우선, 같은 역할이면 참여 시각 오름차순)
 */
@Schema(requiredProperties = {"memberCount", "maxMembers", "members"})
public record ProjectMemberListResponse(
        int memberCount,
        int maxMembers,
        List<MemberSummary> members
) {

    /**
     * 참여자 1명.
     *
     * <p>"나" 배지는 FE가 {@code /users/me}의 {@code userId}와 비교해 표시하므로 별도 플래그를 주지 않는다.
     * 이메일은 개인 식별정보라 목록에 포함하지 않는다. (§3.2.8)
     *
     * @param userId          사용자 ID — 의견·검토의 작성자 매칭 키
     * @param profileImageUrl 프로필 이미지 URL — 없으면 직렬화에서 제외된다
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"userId", "name", "role", "joinedAt"})
    public record MemberSummary(
            Long userId,
            String name,
            String profileImageUrl,
            ProjectMemberRole role,
            LocalDateTime joinedAt
    ) {

        public static MemberSummary from(ProjectMember member) {
            User user = member.getUser();
            return new MemberSummary(
                    user.getId(),
                    user.getName(),
                    user.getProfileImageUrl(),
                    member.getRole(),
                    member.getJoinedAt());
        }
    }

    public static ProjectMemberListResponse from(List<ProjectMember> members) {
        return new ProjectMemberListResponse(
                members.size(),
                Project.MAX_MEMBERS,
                members.stream().map(MemberSummary::from).toList());
    }
}
