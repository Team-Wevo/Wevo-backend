package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 멤버십과 역할을 검증하는 공통 인가 컴포넌트.
 *
 * <p>프로젝트에 참여하지 않은 사용자는 {@link ErrorCode#NOT_PROJECT_MEMBER}, 참여 중이지만
 * 요구 역할과 다른 사용자는 {@link ErrorCode#FORBIDDEN}으로 구분한다.
 *
 * <p>역할 검사를 통과하면 조회한 {@link ProjectMember}를 그대로 반환한다. 호출 서비스는
 * 권한 검사를 다시 수행하지 않고도 이 멤버십의 사용자·역할 정보를 후속 처리에 사용할 수 있다.
 */
@Component
public class ProjectAccessGuard {

    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAccessGuard(ProjectMemberRepository projectMemberRepository) {
        this.projectMemberRepository = projectMemberRepository;
    }

    /**
     * 요청자가 프로젝트 참여자(OWNER 또는 MEMBER)인지 검증한다.
     *
     * <p>역할 종류는 제한하지 않으며, 프로젝트 멤버십 존재 여부만 확인한다.
     *
     * @return 검증된 프로젝트 멤버십
     * @throws BusinessException 프로젝트 멤버십이 없으면 {@link ErrorCode#NOT_PROJECT_MEMBER}
     */
    public ProjectMember requireParticipant(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));
    }

    /**
     * 요청자가 프로젝트 팀장(OWNER)인지 검증한다.
     *
     * @return OWNER 역할로 검증된 프로젝트 멤버십
     * @throws BusinessException 프로젝트 멤버가 아니면 {@link ErrorCode#NOT_PROJECT_MEMBER},
     *                           MEMBER이면 {@link ErrorCode#FORBIDDEN}
     */
    public ProjectMember requireOwner(Long projectId, Long userId) {
        return requireRole(projectId, userId, ProjectMemberRole.OWNER);
    }

    /**
     * 요청자가 프로젝트 팀원(MEMBER)인지 검증한다. OWNER는 통과하지 않는다.
     *
     * <p>팀 검토 제출처럼 MEMBER에게만 허용되는 기능을 위한 검사이므로, 프로젝트 참여자라는
     * 이유만으로 OWNER까지 허용하지 않는다.
     *
     * @return MEMBER 역할로 검증된 프로젝트 멤버십
     * @throws BusinessException 프로젝트 멤버가 아니면 {@link ErrorCode#NOT_PROJECT_MEMBER},
     *                           OWNER이면 {@link ErrorCode#FORBIDDEN}
     */
    public ProjectMember requireMember(Long projectId, Long userId) {
        return requireRole(projectId, userId, ProjectMemberRole.MEMBER);
    }

    /**
     * 요청자가 프로젝트 참여자인지 검증하고, 검증을 통과했다는 <b>증거</b>를 반환한다.
     *
     * <p>{@link VerifiedProjectAccess}는 이 컴포넌트만 만들 수 있으므로, 이를 인자로 받는
     * 타 도메인의 조회 서비스는 인가를 건너뛴 호출을 컴파일 단계에서 차단할 수 있다.
     * 프로젝트를 {@code JOIN FETCH} 로 함께 로딩하므로 후속 {@code project()} 접근에
     * 추가 쿼리가 나가지 않는다.
     *
     * @throws BusinessException 프로젝트 멤버십이 없으면 {@link ErrorCode#NOT_PROJECT_MEMBER}
     */
    public VerifiedProjectAccess requireParticipantAccess(Long projectId, Long userId) {
        ProjectMember membership = projectMemberRepository
                .findWithProjectByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));
        return VerifiedProjectAccess.of(membership);
    }

    /**
     * 인가 검사를 수행하되, 비멤버({@link ErrorCode#NOT_PROJECT_MEMBER})만 지정한 코드로 바꿔 던진다.
     *
     * <p>존재 숨김(CLAUDE.md §5.6)의 공통 구현이다 — 리소스마다 숨길 코드가 다르므로
     * ({@code PROJECT_NOT_FOUND} / {@code SECTION_NOT_FOUND}) 코드를 인자로 받는다.
     * 역할 부족({@link ErrorCode#FORBIDDEN})과 그 밖의 예외는 그대로 전파한다 — 멤버에게는
     * 리소스 존재를 숨길 이유가 없기 때문이다.
     *
     * <p>상태를 갖지 않는 순수 정책 함수라 {@code static} 으로 둔다 — 협력 객체와의 상호작용이
     * 아니라 규칙 그 자체이므로, 이 가드를 모킹한 단위 테스트에서도 숨김 규칙은 그대로 적용된다.
     *
     * @param hiddenAs    비멤버에게 대신 노출할 "찾을 수 없음" 코드
     * @param accessCheck 수행할 인가 검사
     */
    public static <T> T hidingNonMember(ErrorCode hiddenAs, Supplier<T> accessCheck) {
        try {
            return accessCheck.get();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.NOT_PROJECT_MEMBER) {
                throw new BusinessException(hiddenAs);
            }
            throw e;
        }
    }

    /**
     * 공통 멤버십 검사를 먼저 수행한 뒤 요청 기능에 필요한 역할과 정확히 일치하는지 확인한다.
     */
    private ProjectMember requireRole(Long projectId, Long userId, ProjectMemberRole requiredRole) {
        ProjectMember participant = requireParticipant(projectId, userId);
        if (participant.getRole() != requiredRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return participant;
    }
}
