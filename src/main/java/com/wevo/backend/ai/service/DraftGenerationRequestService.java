package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AiContextAssemblyException;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.service.UserService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OWNER의 초안 생성 요청을 검증하고 멱등 AI 작업을 큐잉하는 내부 application service. */
@Service
public class DraftGenerationRequestService {

    private static final Set<AiJobStatus> REUSABLE_STATUSES =
            Set.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.SUCCEEDED);

    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final AiContextAssembler contextAssembler;
    private final AiJobService aiJobService;
    private final AiProperties aiProperties;
    private final UserService userService;

    public DraftGenerationRequestService(
            SectionAccessGuard sectionAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            AiContextAssembler contextAssembler,
            AiJobService aiJobService,
            AiProperties aiProperties,
            UserService userService
    ) {
        this.sectionAccessGuard = sectionAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.contextAssembler = contextAssembler;
        this.aiJobService = aiJobService;
        this.aiProperties = aiProperties;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public UUID requestDraftGeneration(Long sectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireOwnedSection(sectionId, userId);
        VerifiedProjectAccess access =
                projectAccessGuard.requireParticipantAccess(section.getProject().getId(), userId);
        AssembledAiContext<DraftGenerationContext> assembled;
        try {
            assembled = contextAssembler.assembleDraftGeneration(access, sectionId);
        } catch (AiContextAssemblyException exception) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }

        AiProperties.ModelOptions options =
                aiProperties.optionsFor(AiFeature.DRAFT_GENERATION);
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
                        AiFeature.DRAFT_GENERATION,
                        assembled.snapshot().inputSnapshotHash(),
                        assembled.context().sourceVersion(),
                        DraftGenerationContract.PROMPT_VERSION,
                        DraftGenerationContract.SCHEMA_VERSION,
                        options.model(),
                        options.maxOutputTokens()
                )));
        return result.requestId();
    }

    private void requireExecutable(DraftGenerationContext context) {
        if (context.section().status() != ProjectSectionStatus.SYNTHESIZING) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
        if (context.section().synthesisStale()
                || context.synthesis().opinionGateGeneration()
                != context.section().opinionGateGeneration()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        if (context.synthesis().hasUnresolvedConflict()) {
            throw new BusinessException(ErrorCode.ISSUE_CONFLICT_UNDECIDED);
        }
    }

    private AiJobIdempotencyInput identity(
            ProjectSection section,
            AssembledAiContext<DraftGenerationContext> assembled,
            AiProperties.ModelOptions options
    ) {
        return new AiJobIdempotencyInput(
                AiFeature.DRAFT_GENERATION,
                section.getProject().getId(),
                section.getId(),
                assembled.snapshot().inputSnapshotHash(),
                assembled.context().sourceVersion(),
                DraftGenerationContract.PROMPT_VERSION,
                DraftGenerationContract.SCHEMA_VERSION,
                options.model(),
                options.maxOutputTokens()
        );
    }
}
