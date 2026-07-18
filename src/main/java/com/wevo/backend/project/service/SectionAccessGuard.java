package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 섹션 조회와 프로젝트 역할 검사를 결합하는 공통 인가 컴포넌트.
 *
 * <p>섹션 기반 API가 도메인별로 같은 멤버십 조회를 반복하지 않도록 project 도메인에서
 * 공통 진입점을 제공한다.
 *
 * <p>모든 검사는 섹션 존재 여부를 먼저 확인한 뒤, 섹션이 속한 프로젝트 ID로
 * {@link ProjectAccessGuard}의 역할 검사를 수행한다. 따라서 존재하지 않는 섹션은 역할 조회 없이
 * {@link ErrorCode#SECTION_NOT_FOUND}로 처리된다.
 *
 * <p><b>존재 숨김(CLAUDE.md §5.6)</b> — 섹션 기반 API에서 비멤버는 섹션이 없는 경우와 동일하게
 * {@link ErrorCode#SECTION_NOT_FOUND}(404)로 응답한다. 순번 ID를 훑어 남의 섹션 존재 여부를
 * 알아내지 못하게 하기 위함이며, 멤버지만 역할이 부족한 경우에만 {@link ErrorCode#FORBIDDEN}(403)을 쓴다.
 */
@Component
public class SectionAccessGuard {

    private final ProjectSectionRepository projectSectionRepository;
    private final ProjectAccessGuard projectAccessGuard;

    public SectionAccessGuard(ProjectSectionRepository projectSectionRepository,
                              ProjectAccessGuard projectAccessGuard) {
        this.projectSectionRepository = projectSectionRepository;
        this.projectAccessGuard = projectAccessGuard;
    }

    /**
     * 섹션을 조회하고 요청자가 프로젝트 참여자인지 검증한다.
     *
     * @return 접근 권한이 확인된 섹션
     * @throws BusinessException 섹션이 없거나 프로젝트에 참여하지 않았으면(존재 숨김)
     *                           {@link ErrorCode#SECTION_NOT_FOUND}
     */
    public ProjectSection requireParticipantSection(Long sectionId, Long userId) {
        ProjectSection section = requireSection(sectionId);
        hideNonMember(() -> projectAccessGuard.requireParticipant(section.getProject().getId(), userId));
        return section;
    }

    /**
     * 섹션 행을 <b>배타 잠금(PESSIMISTIC_WRITE)</b>으로 조회하고 요청자가 프로젝트 참여자인지 검증한다.
     *
     * <p>상태 확인 후 쓰기가 이어지는 참여자 공용 경로(의견 임시저장·제출 등) 전용 —
     * 읽기 전용 경로에는 {@link #requireParticipantSection}을 사용한다.
     *
     * @return 접근 권한이 확인된, 잠금이 걸린 섹션
     * @throws BusinessException 섹션이 없거나 프로젝트에 참여하지 않았으면(존재 숨김)
     *                           {@link ErrorCode#SECTION_NOT_FOUND}
     */
    public ProjectSection requireParticipantSectionForUpdate(Long sectionId, Long userId) {
        ProjectSection section = requireSectionForUpdate(sectionId);
        hideNonMember(() -> projectAccessGuard.requireParticipant(section.getProject().getId(), userId));
        return section;
    }

    /**
     * 섹션을 조회하고 요청자가 프로젝트 팀장(OWNER)인지 검증한다.
     *
     * @return OWNER 접근 권한이 확인된 섹션
     * @throws BusinessException 섹션이 없거나 프로젝트에 참여하지 않았으면(존재 숨김)
     *                           {@link ErrorCode#SECTION_NOT_FOUND},
     *                           MEMBER이면 {@link ErrorCode#FORBIDDEN}
     */
    public ProjectSection requireOwnedSection(Long sectionId, Long userId) {
        ProjectSection section = requireSection(sectionId);
        hideNonMember(() -> projectAccessGuard.requireOwner(section.getProject().getId(), userId));
        return section;
    }

    /**
     * 섹션을 조회하고 요청자가 프로젝트 팀원(MEMBER)인지 검증한다. OWNER는 통과하지 않는다.
     *
     * @return MEMBER 접근 권한이 확인된 섹션
     * @throws BusinessException 섹션이 없거나 프로젝트에 참여하지 않았으면(존재 숨김)
     *                           {@link ErrorCode#SECTION_NOT_FOUND},
     *                           OWNER이면 {@link ErrorCode#FORBIDDEN}
     */
    public ProjectSection requireMemberSection(Long sectionId, Long userId) {
        ProjectSection section = requireSection(sectionId);
        hideNonMember(() -> projectAccessGuard.requireMember(section.getProject().getId(), userId));
        return section;
    }

    /**
     * 섹션 행을 <b>배타 잠금(PESSIMISTIC_WRITE)</b>으로 조회하고 요청자가 팀장(OWNER)인지 검증한다.
     *
     * <p>"섹션의 최신 상태 확인 후 쓰기"가 이어지는 팀장 전용 경로(외부 검토 링크 발급 등) 전용 —
     * 같은 섹션 행을 잠그는 본문 저장 플로우와 직렬화되어, 방금 구버전이 된 본문을 스냅샷으로 갖는
     * ACTIVE 링크가 생기는 경합을 막는다. 읽기 전용 경로에는 {@link #requireOwnedSection}을 사용한다.
     *
     * @return OWNER 접근 권한이 확인된, 잠금이 걸린 섹션
     * @throws BusinessException 섹션이 없거나 프로젝트에 참여하지 않았으면(존재 숨김)
     *                           {@link ErrorCode#SECTION_NOT_FOUND},
     *                           MEMBER이면 {@link ErrorCode#FORBIDDEN}
     */
    public ProjectSection requireOwnedSectionForUpdate(Long sectionId, Long userId) {
        ProjectSection section = requireSectionForUpdate(sectionId);
        hideNonMember(() -> projectAccessGuard.requireOwner(section.getProject().getId(), userId));
        return section;
    }

    /**
     * 섹션 행을 <b>배타 잠금(PESSIMISTIC_WRITE)</b>으로 조회하고 요청자가 팀원(MEMBER)인지 검증한다.
     *
     * <p>상태 확인 후 쓰기가 이어지는 경로(팀 검토 업서트 등) 전용 — 같은 섹션에 대한 동시
     * 요청을 직렬화해 "조회 후 insert"가 유니크 제약 충돌로 실패하는 경합을 막는다.
     * 읽기 전용 경로에는 {@link #requireMemberSection}을 사용한다.
     *
     * @return MEMBER 접근 권한이 확인된, 잠금이 걸린 섹션
     * @throws BusinessException 섹션이 없거나 프로젝트에 참여하지 않았으면(존재 숨김)
     *                           {@link ErrorCode#SECTION_NOT_FOUND},
     *                           OWNER이면 {@link ErrorCode#FORBIDDEN}
     */
    public ProjectSection requireMemberSectionForUpdate(Long sectionId, Long userId) {
        ProjectSection section = requireSectionForUpdate(sectionId);
        hideNonMember(() -> projectAccessGuard.requireMember(section.getProject().getId(), userId));
        return section;
    }

    /**
     * 역할 검사에 앞서 대상 섹션을 조회한다.
     */
    private ProjectSection requireSection(Long sectionId) {
        return projectSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
    }

    /**
     * 역할 검사에 앞서 대상 섹션을 배타 잠금으로 조회한다.
     */
    private ProjectSection requireSectionForUpdate(Long sectionId) {
        return projectSectionRepository.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SECTION_NOT_FOUND));
    }

    /**
     * 역할 검사를 수행하되, 비멤버({@link ErrorCode#NOT_PROJECT_MEMBER})는
     * {@link ErrorCode#SECTION_NOT_FOUND}로 바꿔 던진다(존재 숨김 — CLAUDE.md §5.6).
     * 역할 부족({@link ErrorCode#FORBIDDEN})은 그대로 전파한다.
     */
    private void hideNonMember(Supplier<?> roleCheck) {
        try {
            roleCheck.get();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.NOT_PROJECT_MEMBER) {
                throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
            }
            throw e;
        }
    }
}
