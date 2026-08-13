package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.TokenHasher;
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
import java.time.ZoneId;

/**
 * 프로젝트 초대 링크 생성·참여. (제품 정책서 §2.1)
 *
 * <p>재사용 링크 방식만 지원하며, MVP에서는 링크 만료·비활성화·재생성을 두지 않는다.
 * 최대 인원({@link Project#MAX_MEMBERS})을 초과하는 참여는 거부한다.
 */
@Service
@Transactional(readOnly = true)
public class InviteService {

    /** 시간 값은 배포 서버 시간대와 무관하게 KST로 고정한다. (CLAUDE.md §5.4) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final InviteLinkRepository inviteLinkRepository;
    private final UserRepository userRepository;
    private final InviteTokenFactory inviteTokenFactory;
    private final TokenHasher tokenHasher;
    private final String inviteBaseUrl;

    public InviteService(ProjectRepository projectRepository,
                         ProjectMemberRepository projectMemberRepository,
                         InviteLinkRepository inviteLinkRepository,
                         UserRepository userRepository,
                         InviteTokenFactory inviteTokenFactory,
                         TokenHasher tokenHasher,
                         @Value("${app.invite.base-url:http://localhost:3000/invite/}") String inviteBaseUrl) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.inviteLinkRepository = inviteLinkRepository;
        this.userRepository = userRepository;
        this.inviteTokenFactory = inviteTokenFactory;
        this.tokenHasher = tokenHasher;
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

        // 프로젝트 행을 배타 잠금으로 잡아 생성 경로를 직렬화한다. 잠그지 않으면 아직 링크가 없는
        // 프로젝트에서 동시 생성 요청이 둘 다 "활성 링크 없음"을 보고 각자 save 를 시도해, 늦은 쪽이
        // token_hash 유니크 제약(uk_invite_links_token_hash)에서 터진다 — 토큰이 projectId 파생이라
        // 결정적이므로 두 행의 해시가 같다. 멱등이어야 할 생성이 409 로 실패하는 것을 막는다.
        // (참여 경로 joinByToken 도 같은 행을 잠그므로 정원 판정과도 직렬화된다.)
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 보관(삭제)된 프로젝트에는 링크를 재발급하지 않는다. 보관 후에도 OWNER 멤버십은 남으므로
        // 상태 검사가 없으면 삭제한 프로젝트에 링크가 부활해 합류가 재개된다. archive 가 링크 비활성화라는
        // 부수 효과 하나에만 기대지 않고, 보관 상태를 신뢰의 단일 소스로 삼는다. 삭제된 프로젝트는
        // 존재를 숨긴다(§5.6 — 404). (#280)
        if (project.isArchived()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // 토큰은 저장값이 아니라 프로젝트 ID 에서 파생한다 — 해시만 저장하므로 기존 링크를 그대로
        // 돌려주려면 원문을 매번 다시 계산해야 한다. (InviteTokenFactory 참고)
        String rawToken = inviteTokenFactory.tokenFor(projectId);
        String tokenHash = tokenHasher.hash(rawToken);

        // 기존 행이 있으면 해시를 지금 값으로 맞춘다. 반환하는 토큰과 저장된 해시가 어긋나면
        // 멀쩡해 보이는 URL 이 P003 으로 죽는데, 그 상태가 오류 없이 공유된다.
        // (비밀키를 바꿨거나, 해시 도입 전에 원문으로 저장된 행이 남아 있는 경우)
        inviteLinkRepository.findFirstByProjectIdAndIsActiveTrue(projectId)
                .ifPresentOrElse(
                        link -> link.refreshTokenHash(tokenHash),
                        () -> inviteLinkRepository.save(InviteLink.issue(
                                membership.getProject(), membership.getUser(), tokenHash)));

        return InviteLinkResponse.of(rawToken, buildInviteUrl(rawToken));
    }

    /**
     * 참여 전 미리보기. 유효한 활성 토큰이면 프로젝트명·인원을 반환한다. (참여 여부와 무관)
     *
     * @throws BusinessException INVITE_LINK_NOT_FOUND(토큰 무효/비활성)
     */
    public InvitePreviewResponse getInvitePreview(String token) {
        InviteLink link = inviteLinkRepository.findActiveWithProjectByTokenHash(tokenHasher.hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND));
        Project project = link.getProject();
        // 탈퇴자는 세지 않는다 — 정원과 짝이 되는 값이라 "몇 자리 남았는지"를 뜻한다. (§3.3.3)
        long memberCount = projectMemberRepository.countActiveByProjectId(project.getId());
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
        InviteLink link = inviteLinkRepository.findActiveWithProjectByTokenHash(tokenHasher.hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND));

        // 인원 체크~저장을 직렬화하기 위해 프로젝트 행을 잠금 조회한다.
        Project project = projectRepository.findByIdForUpdate(link.getProject().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 잠근 뒤 보관 여부를 재확인한다. 링크를 활성으로 읽은 직후 archive 가 커밋되는 TOCTOU 를 막는다.
        // archive 는 프로젝트 행을 배타 잠금으로 잡지 않으므로, 여기서 잠금 후 재검증해야 이미 보관된
        // 프로젝트에 멤버가 추가되는 것을 차단할 수 있다. 링크가 죽은 것으로 취급한다(§5.6 — 404). (#280)
        if (project.isArchived()) {
            throw new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND);
        }

        // 이미 멤버면 기존 권한 유지 (멱등 — 재참여로 취급하지 않음)
        ProjectMember existing = projectMemberRepository
                .findByProjectIdAndUserId(project.getId(), userId).orElse(null);
        if (existing != null) {
            return ProjectJoinResponse.from(existing);
        }

        // 탈퇴자가 자리를 차지하면 4명을 채웠던 팀은 한 명이 나가도 새 팀원을 받을 수 없다. (§3.3.3)
        if (projectMemberRepository.countActiveByProjectId(project.getId()) >= Project.MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_LIMIT_EXCEEDED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ProjectMember saved = projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectMemberRole.MEMBER)
                .joinedAt(LocalDateTime.now(KST))
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

}
