package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.InviteLink;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.dto.response.InviteLinkResponse;
import com.wevo.backend.project.dto.response.InvitePreviewResponse;
import com.wevo.backend.project.dto.response.ProjectJoinResponse;
import com.wevo.backend.project.repository.InviteLinkRepository;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 프로젝트 초대 링크 생성·참여. (제품 정책서 §2.1)
 *
 * <p>재사용 링크 방식만 지원하며, MVP에서는 링크 만료·비활성화·재생성을 두지 않는다.
 * 최대 인원({@link Project#MAX_MEMBERS})을 초과하는 참여는 거부한다.
 */
@Service
@Transactional(readOnly = true)
public class InviteService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final InviteLinkRepository inviteLinkRepository;
    private final UserRepository userRepository;
    private final String inviteBaseUrl;

    public InviteService(ProjectRepository projectRepository,
                         ProjectMemberRepository projectMemberRepository,
                         InviteLinkRepository inviteLinkRepository,
                         UserRepository userRepository,
                         @Value("${app.invite.base-url:http://localhost:3000/invite/}") String inviteBaseUrl) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.inviteLinkRepository = inviteLinkRepository;
        this.userRepository = userRepository;
        this.inviteBaseUrl = inviteBaseUrl;
    }

    /**
     * 재사용 초대 링크를 생성한다. OWNER 만 호출할 수 있으며, 이미 활성 링크가 있으면 그대로 반환한다(멱등).
     *
     * @throws BusinessException PROJECT_NOT_FOUND(멤버 아님/프로젝트 없음 — 존재 숨김) / FORBIDDEN(멤버지만 OWNER 아님)
     */
    @Transactional
    public InviteLinkResponse createInviteLink(Long userId, Long projectId) {
        ProjectMember membership = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        if (membership.getRole() != ProjectMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        InviteLink link = inviteLinkRepository.findFirstByProjectIdAndIsActiveTrue(projectId)
                .orElseGet(() -> inviteLinkRepository.save(
                        InviteLink.issue(membership.getProject(), membership.getUser(), generateToken())));

        return InviteLinkResponse.of(link.getToken(), buildInviteUrl(link.getToken()));
    }

    /**
     * 참여 전 미리보기. 유효한 활성 토큰이면 프로젝트명·인원을 반환한다. (참여 여부와 무관)
     *
     * @throws BusinessException INVITE_LINK_NOT_FOUND(토큰 무효/비활성)
     */
    public InvitePreviewResponse getInvitePreview(String token) {
        InviteLink link = inviteLinkRepository.findActiveWithProjectByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND));
        Project project = link.getProject();
        long memberCount = projectMemberRepository.countByProjectId(project.getId());
        return InvitePreviewResponse.of(project, memberCount);
    }

    /**
     * 초대 링크로 프로젝트에 MEMBER 로 참여한다.
     *
     * <p>이미 멤버면 기존 역할 그대로 멱등하게 반환한다. 인원이 상한에 도달했으면 거부한다.
     * "인원 확인 → 저장" 사이는 프로젝트 행 배타 잠금으로 직렬화해 동시 참여 경합을 막는다.
     *
     * @throws BusinessException INVITE_LINK_NOT_FOUND / PROJECT_MEMBER_LIMIT_EXCEEDED / USER_NOT_FOUND
     */
    @Transactional
    public ProjectJoinResponse joinByToken(Long userId, String token) {
        InviteLink link = inviteLinkRepository.findActiveWithProjectByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND));

        // 인원 체크~저장을 직렬화하기 위해 프로젝트 행을 잠금 조회한다.
        Project project = projectRepository.findByIdForUpdate(link.getProject().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 이미 멤버면 기존 권한 유지 (멱등 — 재참여로 취급하지 않음)
        ProjectMember existing = projectMemberRepository
                .findByProjectIdAndUserId(project.getId(), userId).orElse(null);
        if (existing != null) {
            return ProjectJoinResponse.from(existing);
        }

        if (projectMemberRepository.countByProjectId(project.getId()) >= Project.MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_LIMIT_EXCEEDED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ProjectMember saved = projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectMemberRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build());

        return ProjectJoinResponse.from(saved);
    }

    /**
     * 공유용 초대 URL을 만든다. base-url에 trailing slash가 있든 없든 정확히 하나만 붙여
     * {@code .../inviteTOKEN} 같은 잘못된 링크를 막는다.
     */
    private String buildInviteUrl(String token) {
        String base = inviteBaseUrl.endsWith("/") ? inviteBaseUrl : inviteBaseUrl + "/";
        return base + token;
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
