package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.ProjectMember;
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
     * 섹션을 조회하고 요청자가 프로젝트 참여자인지 검증하되, <b>요청자의 역할까지</b> 함께 반환한다.
     *
     * <p>참여자면 통과하지만 응답 범위는 팀장(OWNER)에게만 넓히는 조회 API를 위한 진입점이다.
     * (의견 수집 현황 — 팀원은 집계만, 팀장은 멤버별 상태까지. 정책서 §4.5)
     * 역할 부족을 오류로 막는 것이 아니므로 {@link #requireOwnedSection}을 쓸 수 없고,
     * {@link #requireParticipantSection} 뒤에 역할 검사를 덧붙이면 멤버십을 두 번 조회하게 된다.
     *
     * @return 접근 권한이 확인된 섹션과 요청자의 역할
     * @throws BusinessException 섹션이 없거나 프로젝트에 참여하지 않았으면(존재 숨김)
     *                           {@link ErrorCode#SECTION_NOT_FOUND}
     */
    public VerifiedParticipantSection requireParticipantSectionWithRole(Long sectionId, Long userId) {
        ProjectSection section = requireSection(sectionId);
        ProjectMember membership = ProjectAccessGuard.hidingNonMember(
                ErrorCode.SECTION_NOT_FOUND,
                () -> projectAccessGuard.requireParticipant(section.getProject().getId(), userId));
        return VerifiedParticipantSection.of(section, membership);
    }

    /**
     * 섹션을 조회하고 요청자가 프로젝트 참여자인지 검증한 뒤, 검증 <b>증거</b>를 반환한다.
     *
     * <p>{@link VerifiedProjectAccess}를 인자로 요구하는 조회 서비스(예:
     * {@code SectionDraftEvidenceQueryService})를 섹션 ID로 진입하는 API가 호출할 때 쓴다.
     * {@link #requireParticipantSection} 뒤에 {@link ProjectAccessGuard#requireParticipantAccess}를
     * 직접 이어 붙이면 멤버십을 두 번 조회하게 되고, 두 번째 호출이 존재 숨김 밖에 있어 그 사이
     * 멤버십이 사라졌을 때 {@code 404 S001} 대신 {@code 403 P002}가 새어 나간다. 숨김 적용 지점을
     * 하나로 유지하기 위한 진입점이다.
     *
     * @return 접근 권한이 확인된 프로젝트 접근 증거
     * @throws BusinessException 섹션이 없거나 프로젝트에 참여하지 않았으면(존재 숨김)
     *                           {@link ErrorCode#SECTION_NOT_FOUND}
     */
    public VerifiedProjectAccess requireParticipantAccessForSection(Long sectionId, Long userId) {
        ProjectSection section = requireSection(sectionId);
        return ProjectAccessGuard.hidingNonMember(
                ErrorCode.SECTION_NOT_FOUND,
                () -> projectAccessGuard.requireParticipantAccess(
                        section.getProject().getId(), userId));
    }

    /**
     * 이미 검증된 project 접근과 section 소속의 일치를 확인해 내부 조회용 증거를 발급한다.
     *
     * <p>AI use case처럼 project 권한 검증을 먼저 수행한 흐름에서 중복 멤버십 조회 없이 사용한다.
     * 다른 project의 section이면 빈 결과 대신 명시적으로 실패한다.
     */
    public VerifiedSectionAccess verifySectionAccess(
            VerifiedProjectAccess projectAccess, Long sectionId
    ) {
        if (projectAccess == null) {
            throw new IllegalArgumentException("검증된 project 접근 정보는 필수입니다.");
        }
        ProjectSection section = requireSection(sectionId);
        if (!projectAccess.projectId().equals(section.getProject().getId())) {
            throw new IllegalStateException("검증된 project와 대상 section의 소속이 일치하지 않습니다.");
        }
        return VerifiedSectionAccess.of(section);
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
     *
     * <p>숨김 규칙 자체는 {@link ProjectAccessGuard#hidingNonMember}가 소유하고,
     * 여기서는 섹션 기반 API가 쓸 숨김 코드만 고정한다.
     */
    private void hideNonMember(Supplier<?> roleCheck) {
        ProjectAccessGuard.hidingNonMember(ErrorCode.SECTION_NOT_FOUND, roleCheck);
    }
}
