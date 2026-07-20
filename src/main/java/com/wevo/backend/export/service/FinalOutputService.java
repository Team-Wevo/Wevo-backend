package com.wevo.backend.export.service;

import com.wevo.backend.export.dto.response.FinalOutputResponse;
import com.wevo.backend.export.dto.response.FinalOutputResponse.SectionOutput;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.ConfirmedSectionContent;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
import com.wevo.backend.section.service.SectionConfirmationSummary;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        VerifiedProjectAccess access = requireParticipantAccess(projectId, userId);
        Project project = access.project();

        SectionConfirmationSummary summary = sectionConfirmationQueryService.getConfirmationSummary(access);
        if (!summary.allConfirmed()) {
            return FinalOutputResponse.notReady(project, summary);
        }

        return FinalOutputResponse.ready(project, confirmedContents(access, summary.totalCount()));
    }

    /**
     * 각 섹션의 확정본을 섹션 순서대로 조립한다.
     *
     * <p>확정된 섹션에는 {@code confirmedVersion} 에 해당하는 초안이 반드시 있어야 한다.
     * 개수가 맞지 않으면 <b>서버 측 데이터 정합성이 깨진 상태</b>다 — 확정 처리와 초안 이력이
     * 어긋났다는 뜻이며, 클라이언트가 요청을 바꿔 해결할 수 있는 상태 충돌(409)이 아니다.
     * 따라서 {@code 500} 으로 실패시키고(일부가 빠진 결과물을 완성본으로 내보내지 않는다),
     * 재시도 유도 문구만 노출한다.
     */
    private List<SectionOutput> confirmedContents(VerifiedProjectAccess access, int expectedCount) {
        List<ConfirmedSectionContent> contents =
                sectionConfirmationQueryService.findConfirmedContents(access);
        if (contents.size() != expectedCount) {
            log.error("최종 결과물 조립 실패 — 확정 섹션 {} 개 중 확정본 {} 건만 조회됨. projectId={}",
                    expectedCount, contents.size(), access.projectId());
            throw new BusinessException(ErrorCode.FINAL_OUTPUT_ASSEMBLY_FAILED);
        }

        return contents.stream()
                .map(content -> new SectionOutput(content.order(), content.title(), content.content()))
                .toList();
    }

    /**
     * 요청자가 프로젝트 참여자인지 검증하고, 검증 증거를 반환한다.
     *
     * <p>멤버가 아니면 {@code 404 P001} 로 <b>존재 자체를 숨긴다</b>
     * (CLAUDE.md §5.6) — 순번 ID 를 훑어 남의 프로젝트 존재 여부를 알아내지 못하게 한다.
     * 숨김 규칙 자체는 {@link ProjectAccessGuard#hidingNonMember} 가 소유한다.
     */
    private VerifiedProjectAccess requireParticipantAccess(Long projectId, Long userId) {
        return ProjectAccessGuard.hidingNonMember(ErrorCode.PROJECT_NOT_FOUND,
                () -> projectAccessGuard.requireParticipantAccess(projectId, userId));
    }
}
