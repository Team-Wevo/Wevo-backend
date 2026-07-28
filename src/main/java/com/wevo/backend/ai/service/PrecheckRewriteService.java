package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiSectionCheck;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.ai.repository.AiSectionCheckRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.service.AiRewriteDraftCreateCommand;
import com.wevo.backend.section.service.AiRewriteDraftCreateResult;
import com.wevo.backend.section.service.AiRewriteDraftWriter;
import com.wevo.backend.section.service.SectionAiContextQueryService;
import com.wevo.backend.section.service.SectionPrecheckStateService;
import com.wevo.backend.section.service.SectionVersionedContent;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마지막 성공 precheck의 전체 rewrite를 lease·전체 snapshot 검증 후 한 번 적용한다. */
@Service
public class PrecheckRewriteService {

    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final SectionPrecheckStateService precheckStateService;
    private final SectionAiContextQueryService sectionQueryService;
    private final AiContextAssembler contextAssembler;
    private final AiJobRepository jobRepository;
    private final AiSectionCheckRepository checkRepository;
    private final AiRewriteDraftWriter draftWriter;

    public PrecheckRewriteService(
            SectionAccessGuard sectionAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            SectionPrecheckStateService precheckStateService,
            SectionAiContextQueryService sectionQueryService,
            AiContextAssembler contextAssembler,
            AiJobRepository jobRepository,
            AiSectionCheckRepository checkRepository,
            AiRewriteDraftWriter draftWriter
    ) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.precheckStateService = precheckStateService;
        this.sectionQueryService = sectionQueryService;
        this.contextAssembler = contextAssembler;
        this.jobRepository = jobRepository;
        this.checkRepository = checkRepository;
        this.draftWriter = draftWriter;
    }

    @Transactional
    public PrecheckRewriteApplyResult apply(
            Long sectionId,
            Long userId,
            UUID requestId,
            Integer checkedContentVersion
    ) {
        if (requestId == null || checkedContentVersion == null
                || checkedContentVersion <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        ProjectSection section =
                sectionAccessGuard.requireParticipantSection(sectionId, userId);
        VerifiedProjectAccess access = projectAccessGuard.requireParticipantAccess(
                section.getProject().getId(), userId);

        // target와 직접 상위를 먼저 잠가 snapshot 대조부터 append까지 입력이 변하지 않게 한다.
        precheckStateService.lockInputSections(access, sectionId);
        AiSectionCheck check = checkRepository
                .findBySectionAndRequestIdForUpdate(sectionId, requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT));
        requireCurrentSuccessfulResult(sectionId, check);
        SectionVersionedContent latest = sectionQueryService.getLatestDraft(access, sectionId);

        // 성공 적용이 lease를 해제하므로 멱등 판정을 lease·요청 version 검사보다 먼저 한다.
        if (check.isRewriteApplied()) {
            if (check.getAppliedContentVersion().equals(latest.contentVersion())
                    && check.getCheckedDraftId().equals(latest.draftId())) {
                return new PrecheckRewriteApplyResult(
                        latest.contentVersion(), section.getStatus(), List.of());
            }
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        if (!section.getStatus().allowsDraftEditing()
                || section.getAiCheckStatus() != AiCheckStatus.CURRENT
                || !check.getCheckedContentVersion().equals(checkedContentVersion)
                || !check.getCheckedContentVersion().equals(latest.contentVersion())
                || !check.getCheckedDraftId().equals(latest.draftId())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        AssembledAiContext<DraftReviewContext> current =
                contextAssembler.assembleDraftReview(access, sectionId);
        if (!check.getInputSnapshotHash().equals(
                current.snapshot().inputSnapshotHash())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        AiRewriteDraftCreateResult applied = draftWriter.append(
                new AiRewriteDraftCreateCommand(
                        sectionId,
                        userId,
                        latest.contentVersion(),
                        check.getRewriteContent()
                )
        );
        check.bindAppliedRewrite(applied.draftId(), applied.contentVersion());
        return new PrecheckRewriteApplyResult(
                applied.contentVersion(), applied.sectionStatus(), applied.driftedSections());
    }

    private void requireCurrentSuccessfulResult(Long sectionId, AiSectionCheck check) {
        AiJob latestSuccessful = jobRepository
                .findTopByProjectSection_IdAndFeatureAndStatusOrderByCompletedAtDescIdDesc(
                        sectionId, AiFeature.DRAFT_REVIEW, AiJobStatus.SUCCEEDED)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT));
        if (!latestSuccessful.getResultId().equals(check.getId())
                || !latestSuccessful.getRequestId().equals(
                check.getSourceJob().getRequestId())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }
}
