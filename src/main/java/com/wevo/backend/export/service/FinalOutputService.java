package com.wevo.backend.export.service;

import com.wevo.backend.export.dto.response.FinalOutputResponse;
import com.wevo.backend.export.dto.response.FinalOutputResponse.SectionOutput;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.section.service.ConfirmedSectionContent;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
import com.wevo.backend.section.service.SectionConfirmationSummary;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최종 결과물(완성본) 조립. (API_SPEC §3.6.1, 정책서 §2.3)
 *
 * <p>확정된 섹션의 <b>확정본</b>({@code confirmedVersion} 기준 본문)을 섹션 순서대로 이어 붙여
 * 하나의 결과물로 반환한다.
 * 복사·미리보기는 FE가 이 JSON으로 처리한다.
 *
 * <p>섹션 데이터는 리포지토리를 직접 보지 않고 section 도메인이 공개한
 * {@link SectionConfirmationQueryService} 로만 조회한다. (CLAUDE.md §6)
 */
@Service
@Transactional(readOnly = true)
public class FinalOutputService {

    private final ProjectAccessGuard projectAccessGuard;
    private final SectionConfirmationQueryService sectionConfirmationQueryService;

    public FinalOutputService(ProjectAccessGuard projectAccessGuard,
                              SectionConfirmationQueryService sectionConfirmationQueryService) {
        this.projectAccessGuard = projectAccessGuard;
        this.sectionConfirmationQueryService = sectionConfirmationQueryService;
    }

    /**
     * 프로젝트의 최종 결과물을 조회한다. (프로젝트 멤버 전용)
     *
     * <p>모든 섹션이 {@code CONFIRMED} 일 때만 본문을 조립한다. 하나라도 미확정이면
     * {@code ready=false} 와 진행도만 반환한다. (정책서 §2.3 )
     *
     * @throws BusinessException 프로젝트가 없거나 내가 멤버가 아니면 존재 숨김 규칙에 따라
     *                           {@link ErrorCode#PROJECT_NOT_FOUND}({@code P001})
     */
    public FinalOutputResponse getFinalOutput(Long projectId, Long userId) {
        Project project = requireParticipantProject(projectId, userId);

        SectionConfirmationSummary summary = sectionConfirmationQueryService.getConfirmationSummary(projectId);
        if (!summary.allConfirmed()) {
            return FinalOutputResponse.notReady(project, summary.confirmedCount(), summary.totalCount());
        }

        return FinalOutputResponse.ready(project, confirmedContents(projectId, summary.totalCount()));
    }

    /**
     * 각 섹션의 확정본을 섹션 순서대로 조립한다.
     *
     * <p>확정된 섹션에는 {@code confirmedVersion} 에 해당하는 초안이 반드시 있어야 한다.
     * 개수가 맞지 않으면 확정 처리와 초안 이력이 어긋난 비정상 상태이므로, 일부가 빠진 결과물을
     * 완성본으로 내보내지 않고 실패시킨다.
     */
    private List<SectionOutput> confirmedContents(Long projectId, int expectedCount) {
        List<ConfirmedSectionContent> contents =
                sectionConfirmationQueryService.findConfirmedContents(projectId);
        if (contents.size() != expectedCount) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        return contents.stream()
                .map(content -> new SectionOutput(content.order(), content.title(), content.content()))
                .toList();
    }

    /**
     * 요청자가 프로젝트 참여자인지 검증하고 프로젝트를 반환한다.
     *
     * <p>멤버가 아니면 {@code 404 P001} 로 <b>존재 자체를 숨긴다</b>
     * (CLAUDE.md §5.6) — 순번 ID 를 훑어 남의 프로젝트 존재 여부를 알아내지 못하게 한다.
     */
    private Project requireParticipantProject(Long projectId, Long userId) {
        try {
            ProjectMember membership = projectAccessGuard.requireParticipant(projectId, userId);
            return membership.getProject();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.NOT_PROJECT_MEMBER) {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
            }
            throw e;
        }
    }
}
