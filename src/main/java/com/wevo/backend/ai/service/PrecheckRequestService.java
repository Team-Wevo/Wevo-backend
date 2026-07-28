package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.SectionAiContextQueryService;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 멤버의 사전 검토 요청을 검증하고 snapshot 멱등 AI 작업을 큐잉한다. */
@Service
public class PrecheckRequestService {

    private static final Set<AiJobStatus> REUSABLE_STATUSES =
            Set.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.SUCCEEDED);
    private static final Set<ProjectSectionStatus> EXECUTABLE_STATUSES =
            Set.of(ProjectSectionStatus.DRAFTING, ProjectSectionStatus.REVIEWING);

    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final SectionAiContextQueryService sectionQueryService;
    private final AiContextAssembler contextAssembler;
    private final AiJobService aiJobService;
    private final AiProperties aiProperties;
    private final UserService userService;

    public PrecheckRequestService(
            SectionAccessGuard sectionAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            SectionAiContextQueryService sectionQueryService,
            AiContextAssembler contextAssembler,
            AiJobService aiJobService,
            AiProperties aiProperties,
            UserService userService
    ) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.sectionQueryService = sectionQueryService;
        this.contextAssembler = contextAssembler;
        this.aiJobService = aiJobService;
        this.aiProperties = aiProperties;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public UUID requestPrecheck(Long sectionId, Long userId) {
        ProjectSection section =
                sectionAccessGuard.requireParticipantSection(sectionId, userId);
        VerifiedProjectAccess access = projectAccessGuard.requireParticipantAccess(
                section.getProject().getId(), userId);

        // S003 계약을 context 조립 오류로 감싸지 않고 Provider 호출 전에 명시적으로 반환한다.
        sectionQueryService.getLatestDraft(access, sectionId);
        AssembledAiContext<DraftReviewContext> assembled =
                contextAssembler.assembleDraftReview(access, sectionId);
        AiProperties.ModelOptions options = aiProperties.optionsFor(AiFeature.DRAFT_REVIEW);
        AiJobIdempotencyInput identity = identity(section, assembled, options);
        Optional<AiJobCreateResult> existing = aiJobService.findLatest(identity);
        if (existing.isPresent() && REUSABLE_STATUSES.contains(existing.get().status())) {
            return existing.get().requestId();
        }

        requireExecutable(assembled.context());
        User requestedBy = userService.getUserReference(userId);
        AiJobCreateResult result = existing
                .map(previous -> aiJobService.retry(previous.requestId(), requestedBy))
                .orElseGet(() -> aiJobService.createOrGet(new AiJobCreateCommand(
                        section.getProject(),
                        section,
                        requestedBy,
                        AiFeature.DRAFT_REVIEW,
                        assembled.snapshot().inputSnapshotHash(),
                        assembled.context().sourceVersion(),
                        DraftReviewContract.PROMPT_VERSION,
                        DraftReviewContract.SCHEMA_VERSION,
                        options.model(),
                        options.maxOutputTokens()
                )));
        return result.requestId();
    }

    private void requireExecutable(DraftReviewContext context) {
        if (!EXECUTABLE_STATUSES.contains(context.section().status())) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
    }

    private AiJobIdempotencyInput identity(
            ProjectSection section,
            AssembledAiContext<DraftReviewContext> assembled,
            AiProperties.ModelOptions options
    ) {
        return new AiJobIdempotencyInput(
                AiFeature.DRAFT_REVIEW,
                section.getProject().getId(),
                section.getId(),
                assembled.snapshot().inputSnapshotHash(),
                assembled.context().sourceVersion(),
                DraftReviewContract.PROMPT_VERSION,
                DraftReviewContract.SCHEMA_VERSION,
                options.model(),
                options.maxOutputTokens()
        );
    }
}
