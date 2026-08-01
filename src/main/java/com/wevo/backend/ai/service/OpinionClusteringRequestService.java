package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.OpinionClusteringContext;
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

/** OWNER의 명시 실행을 멱등 AI 작업으로 큐잉한다. */
@Service
public class OpinionClusteringRequestService {

    private static final Set<AiJobStatus> REUSABLE_STATUSES =
            Set.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.SUCCEEDED);

    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final AiContextAssembler contextAssembler;
    private final AiJobService aiJobService;
    private final AiProperties aiProperties;
    private final UserService userService;

    public OpinionClusteringRequestService(
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
    public UUID requestClustering(Long sectionId, Long userId) {
        ProjectSection section = sectionAccessGuard.requireOwnedSection(sectionId, userId);
        VerifiedProjectAccess access = projectAccessGuard.requireParticipantAccess(
                section.getProject().getId(), userId);
        AssembledAiContext<OpinionClusteringContext> assembled =
                contextAssembler.assembleOpinionClustering(access, sectionId);
        AiProperties.ModelOptions options = aiProperties.optionsFor(AiFeature.OPINION_CLUSTERING);
        AiJobIdempotencyInput identity = identity(section, assembled, options);

        Optional<AiJobCreateResult> existing = aiJobService.findLatest(identity);
        if (existing.isPresent() && REUSABLE_STATUSES.contains(existing.get().status())) {
            return existing.get().requestId();
        }

        validateNewExecution(section, assembled.context());
        User requestedBy = userService.getUserReference(userId);
        AiJobCreateResult result = existing
                .map(previous -> aiJobService.retry(previous.requestId(), requestedBy))
                .orElseGet(() -> aiJobService.createOrGet(new AiJobCreateCommand(
                        section.getProject(),
                        section,
                        requestedBy,
                        AiFeature.OPINION_CLUSTERING,
                        assembled.snapshot().inputSnapshotHash(),
                        OpinionClusteringContract.SOURCE_VERSION,
                        OpinionClusteringContract.PROMPT_VERSION,
                        OpinionClusteringContract.SCHEMA_VERSION,
                        options.model(),
                        options.maxOutputTokens()
                )));
        return result.requestId();
    }

    private void validateNewExecution(ProjectSection section, OpinionClusteringContext context) {
        if (section.getStatus() == ProjectSectionStatus.COLLECTING) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
        if (context.opinions().isEmpty()) {
            throw new BusinessException(ErrorCode.NO_SUBMITTED_OPINION);
        }
        if (context.opinions().size() < OpinionClusteringContract.MIN_OPINION_COUNT) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private AiJobIdempotencyInput identity(
            ProjectSection section,
            AssembledAiContext<OpinionClusteringContext> assembled,
            AiProperties.ModelOptions options
    ) {
        return new AiJobIdempotencyInput(
                AiFeature.OPINION_CLUSTERING,
                section.getProject().getId(),
                section.getId(),
                assembled.snapshot().inputSnapshotHash(),
                OpinionClusteringContract.SOURCE_VERSION,
                OpinionClusteringContract.PROMPT_VERSION,
                OpinionClusteringContract.SCHEMA_VERSION,
                options.model(),
                options.maxOutputTokens()
        );
    }
}
